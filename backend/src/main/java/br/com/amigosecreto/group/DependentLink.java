package br.com.amigosecreto.group;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Um dependente e o responsável dele no grupo (feature 004). Nunca pares do sorteio. */
public record DependentLink(UUID dependentId, UUID guardianId) {

    /** Mapa dependente → responsável, como o sorteio e a viabilidade usam (research R35). */
    public static Map<UUID, UUID> toGuardianMap(List<DependentLink> links) {
        Map<UUID, UUID> guardians = new HashMap<>();
        links.forEach(link -> guardians.put(link.dependentId(), link.guardianId()));
        return guardians;
    }
}
