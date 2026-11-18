package br.com.amigosecreto.draw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Sorteio do amigo secreto (research R6, Constituição II): embaralhamento Fisher–Yates com rejeição.
 * Se alguém tirar a si mesmo, o resultado inteiro é descartado e refeito — o que dá uma distribuição
 * uniforme entre todos os derangements (permutações sem ponto fixo). A chance de aceitar cada tentativa
 * é ~1/e (≈ 37%), então 1.000 tentativas só se esgotam com um gerador quebrado.
 *
 * <p>Classe pura (sem Spring): recebe o gerador de números, que em produção é um {@code SecureRandom}.
 */
public class DerangementGenerator {

    public static final int MIN_PARTICIPANTS = 3;
    static final int MAX_ATTEMPTS = 1_000;

    private final RandomGenerator random;

    public DerangementGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** Mapa quem tira → quem é tirado: todos dão e recebem exatamente uma vez, ninguém tira a si mesmo. */
    public Map<UUID, UUID> generate(List<UUID> participants) {
        if (participants.size() < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException("São necessários pelo menos 3 participantes");
        }
        if (new HashSet<>(participants).size() != participants.size()) {
            throw new IllegalArgumentException("Participantes repetidos");
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<UUID> receivers = shuffle(participants);
            if (!hasFixedPoint(participants, receivers)) {
                return toPairs(participants, receivers);
            }
        }
        throw new IllegalStateException("Não foi possível gerar o sorteio sem ninguém tirar a si mesmo");
    }

    private List<UUID> shuffle(List<UUID> participants) {
        List<UUID> copy = new ArrayList<>(participants);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            UUID swap = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, swap);
        }
        return copy;
    }

    private static boolean hasFixedPoint(List<UUID> givers, List<UUID> receivers) {
        for (int i = 0; i < givers.size(); i++) {
            if (givers.get(i).equals(receivers.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static Map<UUID, UUID> toPairs(List<UUID> givers, List<UUID> receivers) {
        Map<UUID, UUID> pairs = new HashMap<>();
        for (int i = 0; i < givers.size(); i++) {
            pairs.put(givers.get(i), receivers.get(i));
        }
        // Defesa em profundidade: bijeção completa antes de devolver
        if (pairs.size() != givers.size() || !new HashSet<>(pairs.values()).equals(new HashSet<>(givers))) {
            throw new IllegalStateException("Sorteio inconsistente");
        }
        return pairs;
    }
}
