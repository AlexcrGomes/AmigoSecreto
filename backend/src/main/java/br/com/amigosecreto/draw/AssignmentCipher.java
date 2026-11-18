package br.com.amigosecreto.draw;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra de quem foi tirado (research R9): AES-256-GCM, IV aleatório de 12 bytes e tag de 128 bits. A
 * saída é {@code IV‖ciphertext‖tag}. O AAD é {@code groupId‖giverId}: um par copiado para outra linha
 * (outro grupo ou outra pessoa) não decifra. Mensagens de erro nunca contêm IDs.
 */
public class AssignmentCipher {

    static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom random;

    public AssignmentCipher(byte[] key, SecureRandom random) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("A chave do sorteio precisa ter 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
        this.random = random;
    }

    public byte[] encrypt(UUID receiverId, UUID groupId, UUID giverId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(groupId, giverId));
            byte[] sealed = cipher.doFinal(toBytes(receiverId));
            return ByteBuffer.allocate(IV_BYTES + sealed.length)
                    .put(iv)
                    .put(sealed)
                    .array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o sorteio", e);
        }
    }

    public UUID decrypt(byte[] encrypted, UUID groupId, UUID giverId) {
        if (encrypted == null || encrypted.length <= IV_BYTES) {
            throw new IllegalStateException("Par do sorteio inválido");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, encrypted, 0, IV_BYTES));
            cipher.updateAAD(aad(groupId, giverId));
            byte[] plain = cipher.doFinal(encrypted, IV_BYTES, encrypted.length - IV_BYTES);
            ByteBuffer buffer = ByteBuffer.wrap(plain);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (GeneralSecurityException | RuntimeException e) {
            // Chave errada, AAD diferente ou dado adulterado: sem detalhes na mensagem
            throw new IllegalStateException("Não foi possível decifrar o par do sorteio");
        }
    }

    private static byte[] aad(UUID groupId, UUID giverId) {
        return ByteBuffer.allocate(32)
                .put(toBytes(groupId))
                .put(toBytes(giverId))
                .array();
    }

    private static byte[] toBytes(UUID id) {
        return ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
