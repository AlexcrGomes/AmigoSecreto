package br.com.amigosecreto.draw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentCipherTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID GIVER = UUID.randomUUID();
    private static final UUID RECEIVER = UUID.randomUUID();

    private static byte[] key() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return key;
    }

    private final AssignmentCipher cipher = new AssignmentCipher(key(), RANDOM);

    @Test
    void roundTrip() {
        byte[] encrypted = cipher.encrypt(RECEIVER, GROUP, GIVER);

        assertThat(cipher.decrypt(encrypted, GROUP, GIVER)).isEqualTo(RECEIVER);
        assertThat(encrypted).hasSize(12 + 16 + 16); // IV + UUID + tag
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        byte[] first = cipher.encrypt(RECEIVER, GROUP, GIVER);
        byte[] second = cipher.encrypt(RECEIVER, GROUP, GIVER);

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOf(first, 12)).isNotEqualTo(Arrays.copyOf(second, 12));
    }

    @Test
    void pairMovedToAnotherGiverOrGroupDoesNotDecrypt() {
        byte[] encrypted = cipher.encrypt(RECEIVER, GROUP, GIVER);

        assertThatThrownBy(() -> cipher.decrypt(encrypted, GROUP, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(encrypted, UUID.randomUUID(), GIVER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKeyDoesNotDecrypt() {
        byte[] encrypted = cipher.encrypt(RECEIVER, GROUP, GIVER);

        assertThatThrownBy(() -> new AssignmentCipher(key(), RANDOM).decrypt(encrypted, GROUP, GIVER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedByteDoesNotDecrypt() {
        byte[] encrypted = cipher.encrypt(RECEIVER, GROUP, GIVER);
        encrypted[20] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(encrypted, GROUP, GIVER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(RECEIVER.toString());
    }

    @Test
    void truncatedInputDoesNotDecrypt() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[10], GROUP, GIVER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustHave32Bytes() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCipher(new byte[16], RANDOM));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCipher(new byte[33], RANDOM));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssignmentCipher(null, RANDOM));
    }

    @Test
    void ciphertextContainsNoTraceOfTheReceiver() {
        byte[] encrypted = cipher.encrypt(RECEIVER, GROUP, GIVER);

        byte[] raw = ByteBuffer.allocate(16)
                .putLong(RECEIVER.getMostSignificantBits())
                .putLong(RECEIVER.getLeastSignificantBits())
                .array();
        String hex = HexFormat.of().formatHex(encrypted);
        assertThat(hex).doesNotContain(HexFormat.of().formatHex(raw));
        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain(RECEIVER.toString());
    }
}
