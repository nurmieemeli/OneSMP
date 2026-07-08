package gg.nurmi.crate;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record CrateReward(String key, double weight, String displayName, Map<Material, Integer> items,
                           double money, List<String> commands, boolean broadcast) {
}
