package gg.nurmi.crate;

import org.bukkit.Material;

import java.util.List;

public record CrateType(String key, String displayName, String keyName, List<String> keyLore,
                         Material keyMaterial, List<CrateReward> rewards) {
}
