package gg.nurmi.scoreboard;

import gg.nurmi.OneSMPPlugin;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "onesmp_sb";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final OneSMPPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public ScoreboardManager(OneSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleJoin(Player player) {
        Component title = renderTitle(player);
        List<Component> lines = renderLines(player);

        plugin.scheduler().runGlobal(() -> {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            applyLines(board, objective, lines);

            plugin.scheduler().runAtEntity(player, () -> {
                player.setScoreboard(board);
                boards.put(player.getUniqueId(), board);
            }, () -> {});
        });
    }

    public void handleQuit(Player player) {
        boards.remove(player.getUniqueId());
    }

    public void refresh(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Component title = renderTitle(player);
        List<Component> lines = renderLines(player);

        plugin.scheduler().runGlobal(() -> {
            Objective objective = board.getObjective(OBJECTIVE_NAME);
            if (objective != null && !title.equals(objective.displayName())) {
                objective.displayName(title);
            }
            applyLines(board, objective, lines);
        });
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runAtEntity(player, () -> refresh(player), () -> {});
        }
    }

    // Each line is its own team whose "entry" is an invisible unique string and whose prefix holds the actual text - the only way to render arbitrary rich text per sidebar row.
    private void applyLines(Scoreboard board, Objective objective, List<Component> lines) {
        if (objective == null) {
            return;
        }

        int newTotal = lines.size();
        int oldTotal = 0;
        while (board.getTeam("csln_" + oldTotal) != null) {
            oldTotal++;
        }

        for (int i = newTotal; i < oldTotal; i++) {
            Team team = board.getTeam("csln_" + i);
            assert team != null;
            for (String entry : team.getEntries()) {
                board.resetScores(entry);
            }
            team.unregister();
        }

        for (int i = 0; i < newTotal; i++) {
            String entry = entryFor(i);
            Team team = board.getTeam("csln_" + i);
            if (team == null) {
                team = board.registerNewTeam("csln_" + i);
                team.addEntry(entry);
            }
            if (!lines.get(i).equals(team.prefix())) {
                team.prefix(lines.get(i));
            }
            if (newTotal != oldTotal) {
                Score score = objective.getScore(entry);
                score.setScore(newTotal - i);
                score.numberFormat(NumberFormat.blank());
            }
        }
    }

    private Component renderTitle(Player player) {
        String titleFormat = plugin.messages().raw("scoreboard.title");
        return plugin.messages().parse(titleFormat, player);
    }

    private List<Component> renderLines(Player player) {
        List<String> lines = plugin.messages().rawList("scoreboard.lines");
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(plugin.messages().renderRelationalRaw(player, player, line));
        }
        return rendered;
    }

    private String entryFor(int index) {
        return "§" + HEX[(index / 16) % 16] + "§" + HEX[index % 16];
    }
}
