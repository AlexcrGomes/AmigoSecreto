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
    void givesUpAfter1000AttemptsWithAlwaysFixedPoints() {
        ScriptedRandom alwaysIdentity = new ScriptedRandom(); // sempre bound-1 → identidade

        assertThatIllegalStateException()
                .isThrownBy(() -> new DerangementGenerator(alwaysIdentity).generate(people(5)));
        assertThat(alwaysIdentity.calls).isEqualTo(1_000 * 4);
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
}
