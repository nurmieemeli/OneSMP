package gg.nurmi.message;

import gg.nurmi.CanvasSuitePlugin;
import gg.nurmi.util.ConfigMigrator;
import io.github.miniplaceholders.api.MiniPlaceholders;
import io.github.miniplaceholders.api.types.RelationalAudience;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MessageService {

    private final CanvasSuitePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;
    private Component prefixComponent = Component.empty();

    public MessageService(CanvasSuitePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigMigrator.migrate(plugin, "messages.yml");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);

        try (Reader defaultReader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8)) {
            this.messages.setDefaults(YamlConfiguration.loadConfiguration(defaultReader));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load bundled default messages.yml: " + ex.getMessage());
        }

        this.prefixComponent = miniMessage.deserialize(messages.getString("prefix", ""));
    }

    private String raw(String path) {
        return messages.getString(path, path);
    }

    private TagResolver prefixResolver() {
        return TagResolver.resolver("prefix", Tag.inserting(prefixComponent));
    }

    public Component render(Pointered audience, String path, TagResolver... extra) {
        TagResolver resolver = TagResolver.resolver(
                prefixResolver(),
                MiniPlaceholders.audienceGlobalPlaceholders(),
                TagResolver.resolver(extra)
        );
        return miniMessage.deserialize(raw(path), audience, resolver);
    }

    public Component renderRelational(Audience viewer, Audience other, String path, TagResolver... extra) {
        RelationalAudience<Audience> relational = RelationalAudience.from(viewer, other);
        TagResolver resolver = TagResolver.resolver(
                prefixResolver(),
                MiniPlaceholders.relationalGlobalPlaceholders(),
                TagResolver.resolver(extra)
        );
        return miniMessage.deserialize(raw(path), relational, resolver);
    }

    public Component renderRelationalRaw(Audience viewer, Audience other, String rawTemplate, TagResolver... extra) {
        RelationalAudience<Audience> relational = RelationalAudience.from(viewer, other);
        TagResolver resolver = TagResolver.resolver(MiniPlaceholders.relationalGlobalPlaceholders(), TagResolver.resolver(extra));
        return miniMessage.deserialize(rawTemplate, relational, resolver);
    }

    public void send(Audience audience, String path, TagResolver... extra) {
        Pointered target = audience instanceof Pointered pointered ? pointered : Audience.empty();
        audience.sendMessage(render(target, path, extra));
        if (audience instanceof Player player) {
            playFeedbackSound(player, path);
        }
    }

    /**
     * Every message in messages.yml starts with "<prefix><red>" for denials/errors or
     * "<prefix><green>" for successes by convention - used here to play matching feedback sounds
     * without every call site having to say whether it just succeeded or failed.
     */
    private void playFeedbackSound(Player player, String path) {
        String template = raw(path);
        if (template.startsWith("<prefix><red>")) {
            plugin.effects().failure(player);
        } else if (template.startsWith("<prefix><green>")) {
            plugin.effects().success(player);
        }
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    public Component parse(String rawInput, TagResolver... resolvers) {
        return miniMessage.deserialize(rawInput, resolvers);
    }

    public Component parse(String rawInput, Pointered audience, TagResolver... resolvers) {
        return miniMessage.deserialize(rawInput, audience, TagResolver.resolver(resolvers));
    }

    public String escape(String untrusted) {
        return miniMessage.escapeTags(untrusted);
    }

    public String stripTags(String input) {
        return miniMessage.stripTags(input);
    }
}
