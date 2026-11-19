package br.com.amigosecreto.group;

import br.com.amigosecreto.common.ConflictException;
import br.com.amigosecreto.common.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Grupo de amigo secreto (tabela {@code secret_group}, migrações V3 e V5). Os detalhes do evento ficam
 * todos nulos só em grupos criados antes da feature 002 (research R22).
 */
@Entity
@Table(name = "secret_group")
public class SecretGroup implements Persistable<UUID> {

    /** O sorteio precisa de pelo menos 3 participantes (FR-020 da 001). */
    public static final int MIN_PARTICIPANTS_TO_DRAW = 3;

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Código sem hífen (8 caracteres do alfabeto de R5). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private GroupStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "drawn_at")
    private Instant drawnAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "location", length = 120)
    private String location;

    /** Data e horário do evento (meia-noite em Brasília quando não há horário), em UTC. */
    @Column(name = "event_starts_at")
    private Instant eventStartsAt;

    @Column(name = "event_has_time")
    private Boolean eventHasTime;

    /** Quando o grupo deve finalizar: o horário do evento ou o fim do dia (research R13). */
    @Column(name = "finalizes_at")
    private Instant finalizesAt;

    /** Sobe quando data, horário ou local mudam; a confirmação vale para uma versão (FR-035). */
    @Column(name = "details_version", nullable = false)
    private int detailsVersion = 1;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Quando o último sorteio foi desfeito por uma saída aprovada; volta a nulo no novo sorteio. */
    @Column(name = "draw_undone_at")
    private Instant drawUndoneAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Transient
    private boolean isNew = true;

    protected SecretGroup() {}

    public SecretGroup(String name, String code, UUID organizerId, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.name = Objects.requireNonNull(name, "name").strip();
        this.code = Objects.requireNonNull(code, "code");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId");
        this.status = GroupStatus.OPEN;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Sorteio feito: OPEN → DRAWN. Um novo sorteio apaga o aviso de sorteio desfeito. */
    public void markDrawn(Instant when) {
        if (status != GroupStatus.OPEN) {
            throw new IllegalStateException("O grupo já foi sorteado");
        }
        this.status = GroupStatus.DRAWN;
        this.drawnAt = Objects.requireNonNull(when, "when");
        this.drawUndoneAt = null;
    }

    public boolean isOrganizer(UUID userId) {
        return organizerId.equals(userId);
    }

    /**
     * Grava descrição, data, horário e local (FR-001, FR-005, FR-006). Só com o grupo {@code OPEN} ou
     * {@code DRAWN}. A data só é validada quando data ou horário mudam (research R14): editar o local de um
     * grupo com a data vencida é permitido. Mudar data, horário ou local de detalhes já existentes aumenta
     * {@code detailsVersion} (FR-035); a descrição não conta (FR-015).
     *
     * @return se data, horário ou local mudaram (ou foram informados pela primeira vez)
     */
    public boolean updateEventDetails(EventDetails details, EventSchedule schedule) {
        Objects.requireNonNull(details, "details");
        if (status == GroupStatus.FINISHED) {
            throw new ConflictException("group-finished", "Este grupo já foi finalizado.");
        }
        Optional<EventDetails> current = eventDetails(schedule);
        boolean dateOrTimeChanged = current.isEmpty()
                || !current.get().eventDate().equals(details.eventDate())
                || !Objects.equals(current.get().eventTime(), details.eventTime());
        if (dateOrTimeChanged) {
            if (schedule.isPast(details.eventDate())) {
                throw new UnprocessableException(
                        "event-date-in-past", "A data do evento precisa ser hoje ou no futuro.");
            }
            if (details.eventTime() != null && schedule.isTimePast(details.eventDate(), details.eventTime())) {
                throw new UnprocessableException("event-time-in-past", "Esse horário já passou.");
            }
        }

        this.description = details.description();
        boolean scheduleChanged = current.map(c -> !c.sameSchedule(details)).orElse(true);
        if (scheduleChanged) {
            this.location = details.location();
            this.eventStartsAt = schedule.startsAt(details.eventDate(), details.eventTime());
            this.eventHasTime = details.eventTime() != null;
            this.finalizesAt = schedule.finalizesAt(details.eventDate(), details.eventTime());
            if (current.isPresent()) {
                this.detailsVersion++;
            }
        }
        return scheduleChanged;
    }

    /**
     * Por que o sorteio não pode acontecer agora, na ordem de research R16, ou {@code null} se pode. Não
     * olha a situação do grupo: quem chama já recusou o que não está {@code OPEN}.
     *
     * @param totalParticipants participantes do grupo, contando o organizador
     * @param pendingCount participantes que ainda não confirmaram
     */
    public DrawBlockedReason drawBlockedReason(int totalParticipants, long pendingCount, EventSchedule schedule) {
        if (location == null) {
            return DrawBlockedReason.EVENT_DETAILS_REQUIRED;
        }
        if (schedule.hasHappened(finalizesAt)) {
            return DrawBlockedReason.EVENT_DATE_PASSED;
        }
        if (totalParticipants < MIN_PARTICIPANTS_TO_DRAW) {
            return DrawBlockedReason.NOT_ENOUGH_PARTICIPANTS;
        }
        if (pendingCount > 0) {
            return DrawBlockedReason.PARTICIPANTS_PENDING;
        }
        return null;
    }

    /** Os detalhes do evento, ou vazio em grupo antigo que ainda espera o organizador (FR-034). */
    public Optional<EventDetails> eventDetails(EventSchedule schedule) {
        if (location == null) {
            return Optional.empty();
        }
        LocalDate date = schedule.toLocalDate(eventStartsAt);
        LocalTime time = schedule.toLocalTime(eventStartsAt, eventHasTime);
        return Optional.of(new EventDetails(description, date, time, location));
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDrawnAt() {
        return drawnAt;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Instant getEventStartsAt() {
        return eventStartsAt;
    }

    public Boolean getEventHasTime() {
        return eventHasTime;
    }

    public Instant getFinalizesAt() {
        return finalizesAt;
    }

    public int getDetailsVersion() {
        return detailsVersion;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getDrawUndoneAt() {
        return drawUndoneAt;
    }
}
