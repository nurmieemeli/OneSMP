package gg.nurmi.scoreboard;

import gg.nurmi.CanvasSuitePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A real, private-per-player Bukkit Scoreboard/Objective sidebar - unlike the overhead nametags,
 * there's no relational-broadcast constraint here forcing a packet-only approach, so this is the
 * standard supported way to build a sidebar. Each line's text still has to go through a Team's
 * prefix (a fake per-entry "player name" is the only thing the sidebar protocol can display),
 * which is the same trick every scoreboard-line plugin uses, not a workaround specific to us.
 */
public final class ScoreboardManager {

    private static final String OBJECTIVE_NAME = "canvassuite_sb";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final CanvasSuitePlugin plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public ScoreboardManager(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /** Must already be running on the player's own entity thread. */
    public void handleJoin(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, renderTitle(player));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), board);
        applyLines(player, board);
    }

    public void handleQuit(Player player) {
        boards.remove(player.getUniqueId());
    }

    /** Must already be running on the player's own entity thread. */
    public void refresh(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.displayName(renderTitle(player));
        }
        applyLines(player, board);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runAtEntity(player, () -> refresh(player), () -> {});
        }
    }

    private void applyLines(Player player, Scoreboard board) {
        for (Team team : List.copyOf(board.getTeams())) {
            if (team.getName().startsWith("csln_")) {
                for (String entry : team.getEntries()) {
                    board.resetScores(entry);
                }
                team.unregister();
            }
        }

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            return;
        }

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            Component rendered = plugin.messages().renderRelationalRaw(player, player, lines.get(i));
            String entry = entryFor(i);
            Team team = board.registerNewTeam("csln_" + i);
            team.addEntry(entry);
            team.prefix(rendered);
            objective.getScore(entry).setScore(total - i);
        }
    }

    private Component renderTitle(Player player) {
        String titleFormat = plugin.getConfig().getString("scoreboard.title", "<white>Server");
        return plugin.messages().parse(titleFormat, player);
    }

    /** Two legacy color codes render as nothing visible and give 256 guaranteed-unique combinations. */
    private String entryFor(int index) {
        return "§" + HEX[(index / 16) % 16] + "§" + HEX[index % 16];
    }
}
