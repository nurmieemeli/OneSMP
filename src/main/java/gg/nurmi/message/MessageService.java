package gg.nurmi.message;

import gg.nurmi.OneSMPPlugin;
import gg.nurmi.util.ConfigMigrator;
import gg.nurmi.util.LanguageManager;
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
import java.util.List;
import java.util.Objects;

public final class MessageService {

    private final OneSMPPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;
    private Component prefixComponent = Component.empty();

    public MessageService(OneSMPPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        LanguageManager.migrateLegacyFile(plugin, "messages.yml", "lang/en_US/messages.yml");
        String path = LanguageManager.file(plugin, "messages");
        ConfigMigrator.migrate(plugin, path);
        File file = new File(plugin.getDataFolder(), path);
        this.messages = YamlConfiguration.loadConfiguration(file);

        try (Reader defaultReader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(path)), StandardCharsets.UTF_8)) {
            this.messages.setDefaults(YamlConfiguration.loadConfiguration(defaultReader));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not load bundled default " + path + ": " + ex.getMessage());
        }

        this.prefixComponent = miniMessage.deserialize(messages.getString("prefix", ""));
    }

    // Public so callers building Components outside chat (GUI titles/lore, dialogs) can still source their template from messages.yml.
    public String raw(String path) {
        return messages.getString(path, path);
    }

    // For dynamically-keyed lookups (e.g. per-world display names) where the key itself isn't a sensible fallback.
    public String raw(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    public List<String> rawList(String path) {
        return messages.getStringList(path);
    }

    private TagResolver prefixResolver() {
        return TagResolver.resolver("prefix", Tag.inserting(prefixComponent));
    }

    // Invisible markers playFeedbackSound() detects instead of <green>/<red>, so a translated/restyled messages.yml can't break sound feedback.
    private TagResolver feedbackResolver() {
        return TagResolver.resolver(
                TagResolver.resolver("success", Tag.selfClosingInserting(Component.empty())),
                TagResolver.resolver("fail", Tag.selfClosingInserting(Component.empty()))
        );
    }

    public Component render(Pointered audience, String path, TagResolver... extra) {
        return renderRaw(audience, raw(path), extra);
    }

    // For MiniMessage templates from outside messages.yml that still want the shared <prefix>/MiniPlaceholders resolution.
    public Component renderRaw(Pointered audience, String template, TagResolver... extra) {
        TagResolver resolver = TagResolver.resolver(
                prefixResolver(),
                feedbackResolver(),
                MiniPlaceholders.audienceGlobalPlaceholders(),
                TagResolver.resolver(extra)
        );
        return miniMessage.deserialize(template, audience, resolver);
    }

    public Component renderRelational(Audience viewer, Audience other, String path, TagResolver... extra) {
        RelationalAudience<Audience> relational = RelationalAudience.from(viewer, other);
        TagResolver resolver = TagResolver.resolver(
                prefixResolver(),
                feedbackResolver(),
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
        sendRaw(audience, raw(path), extra);
    }

    // For MiniMessage templates authored outside messages.yml - see renderRaw.
    public void sendRaw(Audience audience, String template, TagResolver... extra) {
        Pointered target = audience instanceof Pointered pointered ? pointered : Audience.empty();
        audience.sendMessage(renderRaw(target, template, extra));
        if (audience instanceof Player player) {
            playFeedbackSound(player, template);
        }
    }

    // Relies on the convention that every message starts with <prefix><fail> or <prefix><success>.
    private void playFeedbackSound(Player player, String template) {
        if (template.startsWith("<prefix><fail>")) {
            plugin.effects().failure(player);
        } else if (template.startsWith("<prefix><success>")) {
            plugin.effects().success(player);
        }
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }

    public Component parse(String rawInput, TagResolver... resolvers) {
        return miniMessage.deserialize(rawInput, resolvers);
    }

    // Like parse(), but the template comes from messages.yml at `path` instead of being inlined at the call site.
    public Component text(String path, TagResolver... resolvers) {
        return parse(raw(path), resolvers);
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
