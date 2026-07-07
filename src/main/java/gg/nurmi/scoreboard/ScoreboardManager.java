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

import java.util.ArrayList;
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

    /**
     * Must already be running on the player's own entity thread (to safely resolve per-player
     * placeholder text) - creating/mutating the Scoreboard itself is then dispatched onto the
     * global tick thread, since Canvas throws ("Cannot create new scoreboard async") if a new
     * Scoreboard is created from anywhere else.
     */
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

    /** Must already be running on the player's own entity thread - see {@link #handleJoin(Player)}. */
    public void refresh(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Component title = renderTitle(player);
        List<Component> lines = renderLines(player);

        // Scoreboard/Objective/Team mutations must happen on the global tick thread on Canvas,
        // even for an already-existing (per-player) Scoreboard instance.
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

    /**
     * Must already be running on the global tick thread - mutates the Scoreboard's teams/scores
     * directly. Diffs against whatever's already registered instead of unregistering and
     * recreating every "csln_" team on every call - unregistering a team briefly removes its line
     * from the sidebar before the replacement appears, which is what caused the visible blinking.
     * An existing team's prefix is only touched when its text actually changed.
     */
    private void applyLines(Scoreboard board, Objective objective, List<Component> lines) {
        if (objective == null) {
            return;
        }

        int newTotal = lines.size();
        int oldTotal = 0;
        while (board.getTeam("csln_" + oldTotal) != null) {
            oldTotal++;
        }

        // Only the excess teams beyond the new line count are torn down.
        for (int i = newTotal; i < oldTotal; i++) {
            Team team = board.getTeam("csln_" + i);
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
            // Score only depends on position, so it only needs touching when the line count changed.
            if (newTotal != oldTotal) {
                objective.getScore(entry).setScore(newTotal - i);
            }
        }
    }

    private Component renderTitle(Player player) {
        String titleFormat = plugin.getConfig().getString("scoreboard.title", "<white>Server");
        return plugin.messages().parse(titleFormat, player);
    }

    private List<Component> renderLines(Player player) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(plugin.messages().renderRelationalRaw(player, player, line));
        }
        return rendered;
    }

    /** Two legacy color codes render as nothing visible and give 256 guaranteed-unique combinations. */
    private String entryFor(int index) {
        return "§" + HEX[(index / 16) % 16] + "§" + HEX[index % 16];
    }
}
