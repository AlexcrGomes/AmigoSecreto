package br.com.amigosecreto.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DerangementGeneratorTest {

    private static List<UUID> people(int n) {
        return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
    }

    /** Gerador com respostas pré-definidas para nextInt(bound). */
    private static final class ScriptedRandom implements RandomGenerator {
        private final Deque<Integer> script;
        int calls;

        ScriptedRandom(Integer... values) {
            this.script = new ArrayDeque<>(List.of(values));
        }

        @Override
        public int nextInt(int bound) {
            calls++;
            return script.isEmpty() ? bound - 1 : script.pop();
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void propertyBijectionWithoutFixedPointsIn10000Runs() {
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());
        RandomGenerator sizes = new SecureRandom();

        for (int run = 0; run < 10_000; run++) {
            List<UUID> participants = people(3 + sizes.nextInt(98)); // 3..100
            Map<UUID, UUID> pairs = generator.generate(participants);

            assertThat(pairs.keySet()).containsExactlyInAnyOrderElementsOf(participants);
            assertThat(new HashSet<>(pairs.values())).containsExactlyInAnyOrderElementsOf(participants);
            pairs.forEach((giver, receiver) -> assertThat(receiver).isNotEqualTo(giver));
        }
    }

    @Test
    void shuffleWithAFixedPointIsDiscardedAndRedone() {
        List<UUID> abc = people(3);
        // 1º embaralhamento: nextInt(3)=2, nextInt(2)=1 → identidade (todos tiram a si mesmos)
        // 2º embaralhamento: nextInt(3)=0, nextInt(2)=0 → [b, c, a]
        ScriptedRandom random = new ScriptedRandom(2, 1, 0, 0);

        Map<UUID, UUID> pairs = new DerangementGenerator(random).generate(abc);

        assertThat(random.calls).isEqualTo(4);
        assertThat(pairs)
                .containsEntry(abc.get(0), abc.get(1))
                .containsEntry(abc.get(1), abc.get(2))
                .containsEntry(abc.get(2), abc.get(0));
    }

    @Test
    void givesUpAfterTheMaximumAttemptsWithAlwaysFixedPoints() {
        ScriptedRandom alwaysIdentity = new ScriptedRandom(); // sempre bound-1 → identidade

        assertThatIllegalStateException()
                .isThrownBy(() -> new DerangementGenerator(alwaysIdentity).generate(people(5)));
        assertThat(DerangementGenerator.MAX_ATTEMPTS).isEqualTo(200_000);
        assertThat(alwaysIdentity.calls).isEqualTo(200_000 * 4);
    }

    @Test
    void fewerThanThreeParticipantsIsRejected() {
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());

        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(people(2)));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(List.of()));
    }

    @Test
    void duplicatedParticipantsAreRejected() {
        UUID same = UUID.randomUUID();
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generate(List.of(same, same, UUID.randomUUID())));
    }

    @Test
    void uniformOverDerangementsOfThree() {
        assertUniform(3, 2);
    }

    @Test
    void uniformOverDerangementsOfFour() {
        assertUniform(4, 9);
    }

    /** Cada derangement deve aparecer com frequência dentro de ±5% do esperado em 90.000 execuções. */
    private static void assertUniform(int n, int derangements) {
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());
        List<UUID> participants = people(n);
        Map<List<UUID>, Integer> counts = new HashMap<>();
        int runs = 90_000;

        for (int i = 0; i < runs; i++) {
            Map<UUID, UUID> pairs = generator.generate(participants);
            List<UUID> receivers = new ArrayList<>();
            participants.forEach(giver -> receivers.add(pairs.get(giver)));
            counts.merge(receivers, 1, Integer::sum);
        }

        double expected = (double) runs / derangements;
        assertThat(counts).hasSize(derangements);
        counts.values().forEach(count -> assertThat((double) count).isBetween(expected * 0.95, expected * 1.05));
    }

    // ---- feature 004: responsáveis e dependentes (research R35) ----

    /** Participantes e mapa dependente → responsável a partir dos tamanhos das famílias (1 = sem dependentes). */
    private record Group(List<UUID> participants, Map<UUID, UUID> guardians) {
        static Group of(List<Integer> familySizes) {
            List<UUID> participants = new ArrayList<>();
            Map<UUID, UUID> guardians = new HashMap<>();
            for (int size : familySizes) {
                UUID guardian = UUID.randomUUID();
                participants.add(guardian);
                for (int d = 1; d < size; d++) {
                    UUID dependent = UUID.randomUUID();
                    participants.add(dependent);
                    guardians.put(dependent, guardian);
                }
            }
            return new Group(participants, guardians);
        }
    }

    private static boolean forbidden(Map<UUID, UUID> guardians, UUID giver, UUID receiver) {
        return giver.equals(receiver) || giver.equals(guardians.get(receiver)) || receiver.equals(guardians.get(giver));
    }

    private static void assertValid(Group group, Map<UUID, UUID> pairs) {
        assertThat(pairs.keySet()).containsExactlyInAnyOrderElementsOf(group.participants());
        assertThat(new HashSet<>(pairs.values())).containsExactlyInAnyOrderElementsOf(group.participants());
        pairs.forEach((giver, receiver) -> assertThat(forbidden(group.guardians(), giver, receiver))
                .as("par proibido")
                .isFalse());
    }

    /** Todas as atribuições válidas, por força bruta (n ≤ 7). */
    private static List<List<UUID>> validAssignments(Group group) {
        List<List<UUID>> valid = new ArrayList<>();
        permute(group, new ArrayList<>(), new HashSet<>(), valid);
        return valid;
    }

    private static void permute(Group group, List<UUID> prefix, Set<UUID> used, List<List<UUID>> valid) {
        List<UUID> people = group.participants();
        if (prefix.size() == people.size()) {
            valid.add(List.copyOf(prefix));
            return;
        }
        UUID giver = people.get(prefix.size());
        for (UUID receiver : people) {
            if (!used.contains(receiver) && !forbidden(group.guardians(), giver, receiver)) {
                prefix.add(receiver);
                used.add(receiver);
                permute(group, prefix, used, valid);
                prefix.removeLast();
                used.remove(receiver);
            }
        }
    }

    /** Partições de n em famílias de 1 a 6 pessoas (responsável + até 5 dependentes), sem repetir a ordem. */
    private static List<List<Integer>> familyLayouts(int n, int maxSize) {
        List<List<Integer>> layouts = new ArrayList<>();
        if (n == 0) {
            layouts.add(new ArrayList<>());
            return layouts;
        }
        for (int size = Math.min(n, maxSize); size >= 1; size--) {
            for (List<Integer> rest : familyLayouts(n - size, size)) {
                List<Integer> layout = new ArrayList<>();
                layout.add(size);
                layout.addAll(rest);
                layouts.add(layout);
            }
        }
        return layouts;
    }

    @Test
    void propertyNoGuardianAndDependentDrawEachOther() {
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());
        RandomGenerator random = new SecureRandom();
        int drawn = 0;

        for (int run = 0; run < 2_000; run++) {
            int n = 3 + random.nextInt(98); // 3..100
            List<Integer> sizes = new ArrayList<>();
            int left = n;
            while (left > 0) {
                int size = Math.min(left, 1 + random.nextInt(random.nextBoolean() ? 1 : 6));
                sizes.add(size);
                left -= size;
            }
            Group group = Group.of(sizes);
            if (!DerangementGenerator.isFeasible(group.participants(), group.guardians())) {
                continue;
            }

            assertValid(group, generator.generate(group.participants(), group.guardians()));
            drawn++;
        }
        assertThat(drawn).isGreaterThan(1_000);
    }

    @Test
    void feasibilityMatchesBruteForceForEverySmallGroup() {
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());
        int checked = 0;
        for (int n = 3; n <= 7; n++) {
            for (List<Integer> layout : familyLayouts(n, 6)) {
                Group group = Group.of(layout);
                boolean exists = !validAssignments(group).isEmpty();

                assertThat(DerangementGenerator.isFeasible(group.participants(), group.guardians()))
                        .as("viabilidade de %s", layout)
                        .isEqualTo(exists);
                if (exists) {
                    assertValid(group, generator.generate(group.participants(), group.guardians()));
                } else {
                    assertThatIllegalArgumentException()
                            .isThrownBy(() -> generator.generate(group.participants(), group.guardians()));
                }
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(30);
    }

    @Test
    void anImpossibleGroupIsRejectedWithoutDrawing() {
        Group anaLucasBruno = Group.of(List.of(2, 1));
        ScriptedRandom random = new ScriptedRandom();

        assertThat(DerangementGenerator.isFeasible(anaLucasBruno.participants(), anaLucasBruno.guardians()))
                .isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DerangementGenerator(random)
                        .generate(anaLucasBruno.participants(), anaLucasBruno.guardians()));
        assertThat(random.calls).isZero();
    }

    @Test
    void siblingsMayDrawEachOther() {
        // Ana com Lucas e Bia, mais Bruno: em alguma atribuição válida Lucas tira Bia
        Group group = Group.of(List.of(3, 1));
        UUID bia = group.participants().get(2);

        assertThat(validAssignments(group))
                .anySatisfy(receivers -> assertThat(receivers.get(1)).isEqualTo(bia));
    }

    @Test
    void theWorstRealisticFamilyStillDrawsEveryTime() {
        // responsável + 5 dependentes + 1 pessoa: só 44 de 5.040 permutações são válidas
        Group group = Group.of(List.of(6, 1));
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());

        assertThat(validAssignments(group)).hasSize(44);
        for (int run = 0; run < 200; run++) {
            assertValid(group, generator.generate(group.participants(), group.guardians()));
        }
    }

    @Test
    void uniformOverTheValidAssignmentsWithDependents() {
        // Ana com Lucas e Bia, mais Bruno e Carla
        Group group = Group.of(List.of(3, 1, 1));
        List<List<UUID>> valid = validAssignments(group);
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());
        Map<List<UUID>, Integer> counts = new HashMap<>();
        int runs = 60_000;

        for (int i = 0; i < runs; i++) {
            Map<UUID, UUID> pairs = generator.generate(group.participants(), group.guardians());
            List<UUID> receivers = new ArrayList<>();
            group.participants().forEach(giver -> receivers.add(pairs.get(giver)));
            counts.merge(receivers, 1, Integer::sum);
        }

        double expected = (double) runs / valid.size();
        assertThat(counts.keySet()).containsExactlyInAnyOrderElementsOf(valid);
        // ±8% do esperado: com ~60.000/|válidas| ocorrências por atribuição, fica além de 4 desvios-padrão
        counts.values().forEach(count -> assertThat((double) count).isBetween(expected * 0.92, expected * 1.08));
    }

    @Test
    void withoutDependentsTheConstraintsChangeNothing() {
        List<UUID> participants = people(5);
        DerangementGenerator generator = new DerangementGenerator(new SecureRandom());

        assertThat(DerangementGenerator.isFeasible(participants, Map.of())).isTrue();
        assertThat(DerangementGenerator.isFeasible(people(2), Map.of())).isFalse();
        assertValid(new Group(participants, Map.of()), generator.generate(participants, Map.of()));
    }
}
