package br.com.amigosecreto.draw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Sorteio do amigo secreto (research R6 e R35, Constituição II): embaralhamento Fisher–Yates com rejeição.
 * Se a tentativa tiver alguém tirando a si mesmo — ou, com dependentes (feature 004), um responsável e um
 * dependente seu se tirando, em qualquer sentido —, o resultado inteiro é descartado e refeito. Como cada
 * embaralhamento é uniforme sobre todas as permutações, o resultado aceito é uniforme sobre as atribuições
 * válidas. Nunca se "corrige" um par inválido trocando pares: isso tiraria a uniformidade.
 *
 * <p>Antes de sortear, a viabilidade é verificada por emparelhamento bipartido (caminhos aumentantes de
 * Kuhn): sem atribuição válida, o sorteio é recusado sem consumir o gerador. Com dependentes, a fração de
 * permutações válidas pode ser bem menor que 1/e (pior caso realista: responsável, 5 dependentes e mais 1
 * pessoa, 44 de 5.040); com {@value #MAX_ATTEMPTS} tentativas, esgotar o limite num grupo viável tem
 * probabilidade desprezível.
 *
 * <p>Classe pura (sem Spring): recebe o gerador de números, que em produção é um {@code SecureRandom}.
 */
public class DerangementGenerator {

    public static final int MIN_PARTICIPANTS = 3;
    static final int MAX_ATTEMPTS = 200_000;

    private final RandomGenerator random;

    public DerangementGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** Mapa quem tira → quem é tirado: todos dão e recebem exatamente uma vez, ninguém tira a si mesmo. */
    public Map<UUID, UUID> generate(List<UUID> participants) {
        return generate(participants, Map.of());
    }

    /**
     * Como {@link #generate(List)}, e nenhum responsável tira um dependente seu nem é tirado por ele.
     *
     * @param guardians dependente → responsável (os dois entre os participantes)
     * @throws IllegalArgumentException com menos de 3 participantes, repetidos ou sem atribuição válida
     */
    public Map<UUID, UUID> generate(List<UUID> participants, Map<UUID, UUID> guardians) {
        validate(participants);
        if (!isFeasible(participants, guardians)) {
            throw new IllegalArgumentException("Não há atribuição válida com as restrições de dependentes");
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<UUID> receivers = shuffle(participants);
            if (isValid(participants, receivers, guardians)) {
                return toPairs(participants, receivers);
            }
        }
        throw new IllegalStateException("Não foi possível gerar o sorteio respeitando as regras");
    }

    /**
     * Existe ao menos uma atribuição válida? Emparelhamento perfeito no grafo "quem dá → quem pode receber"
     * (Kuhn, O(n·m)); com menos de 3 participantes, não.
     */
    public static boolean isFeasible(List<UUID> participants, Map<UUID, UUID> guardians) {
        int n = participants.size();
        if (n < MIN_PARTICIPANTS) {
            return false;
        }
        int[] receiverOf = new int[n]; // receptor → índice de quem o tira
        Arrays.fill(receiverOf, -1);
        for (int giver = 0; giver < n; giver++) {
            if (!augment(giver, participants, guardians, receiverOf, new boolean[n])) {
                return false;
            }
        }
        return true;
    }

    private static boolean augment(
            int giver, List<UUID> participants, Map<UUID, UUID> guardians, int[] receiverOf, boolean[] seen) {
        for (int receiver = 0; receiver < participants.size(); receiver++) {
            if (seen[receiver] || !allowed(participants.get(giver), participants.get(receiver), guardians)) {
                continue;
            }
            seen[receiver] = true;
            if (receiverOf[receiver] < 0 || augment(receiverOf[receiver], participants, guardians, receiverOf, seen)) {
                receiverOf[receiver] = giver;
                return true;
            }
        }
        return false;
    }

    private static boolean allowed(UUID giver, UUID receiver, Map<UUID, UUID> guardians) {
        return !giver.equals(receiver)
                && !giver.equals(guardians.get(receiver))
                && !receiver.equals(guardians.get(giver));
    }

    private static void validate(List<UUID> participants) {
        if (participants.size() < MIN_PARTICIPANTS) {
            throw new IllegalArgumentException("São necessários pelo menos 3 participantes");
        }
        if (new HashSet<>(participants).size() != participants.size()) {
            throw new IllegalArgumentException("Participantes repetidos");
        }
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

    private static boolean isValid(List<UUID> givers, List<UUID> receivers, Map<UUID, UUID> guardians) {
        for (int i = 0; i < givers.size(); i++) {
            if (!allowed(givers.get(i), receivers.get(i), guardians)) {
                return false;
            }
        }
        return true;
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
