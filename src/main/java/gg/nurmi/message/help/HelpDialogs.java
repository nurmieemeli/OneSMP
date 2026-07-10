package gg.nurmi.message.help;

import gg.nurmi.OneSMPPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

public final class HelpDialogs {

    private static final int COLUMNS = 2;
    private static final int BUTTON_WIDTH = 150;
    private static final ClickCallback.Options SINGLE_USE = ClickCallback.Options.builder().uses(1).build();

    private HelpDialogs() {
    }

    public static void openRoot(OneSMPPlugin plugin, HelpManager helpManager, Player player) {
        List<HelpCategory> categories = helpManager.categories();
        if (categories.isEmpty()) {
            plugin.messages().sendRaw(player, helpManager.message("empty"));
            return;
        }

        List<ActionButton> buttons = categories.stream()
                .map(category -> categoryButton(plugin, helpManager, category))
                .toList();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(plugin.messages().parse(helpManager.message("dialog-title")))
                        .body(List.of(DialogBody.plainMessage(
                                plugin.messages().parse(helpManager.message("dialog-subtitle")))))
                        .build())
                .type(DialogType.multiAction(buttons).columns(COLUMNS).build()));

        player.showDialog(dialog);
    }

    public static void openCategory(OneSMPPlugin plugin, HelpManager helpManager, Player player, HelpCategory category) {
        List<ActionButton> buttons = category.articles().stream()
                .map(article -> articleButton(plugin, helpManager, category, article))
                .toList();

        ActionButton back = ActionButton.create(
                plugin.messages().parse(helpManager.message("dialog-back")),
                plugin.messages().parse(helpManager.message("dialog-back-to-categories")),
                BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player viewer) {
                        openRoot(plugin, helpManager, viewer);
                    }
                }, SINGLE_USE));

        Component description = plugin.messages().parse(category.hasDescription() ? category.description() : " ");

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(plugin.messages().parse(category.displayName()))
                        .body(List.of(DialogBody.plainMessage(description)))
                        .build())
                .type(buttons.isEmpty()
                        ? DialogType.notice(back)
                        : DialogType.multiAction(buttons).exitAction(back).columns(COLUMNS).build()));

        player.showDialog(dialog);
    }

    public static void openArticle(OneSMPPlugin plugin, HelpManager helpManager, Player player, HelpCategory category, HelpArticle article) {
        Component body = article.body().isEmpty()
                ? plugin.messages().parse(helpManager.message("dialog-no-content"))
                : plugin.messages().parse(String.join("<newline>", article.body()));

        ActionButton back = ActionButton.create(
                plugin.messages().parse(helpManager.message("dialog-back")),
                plugin.messages().parse(helpManager.message("dialog-back-to-category"),
                        Placeholder.component("category", plugin.messages().parse(category.displayName()))),
                BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player viewer) {
                        openCategory(plugin, helpManager, viewer, category);
                    }
                }, SINGLE_USE));

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(plugin.messages().parse(article.title()))
                        .body(List.of(DialogBody.plainMessage(body)))
                        .build())
                .type(DialogType.notice(back)));

        player.showDialog(dialog);
    }

    private static ActionButton categoryButton(OneSMPPlugin plugin, HelpManager helpManager, HelpCategory category) {
        return ActionButton.create(
                plugin.messages().parse(category.displayName()),
                plugin.messages().parse(category.hasDescription() ? category.description() : " "),
                BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player viewer) {
                        openCategory(plugin, helpManager, viewer, category);
                    }
                }, SINGLE_USE));
    }

    private static ActionButton articleButton(OneSMPPlugin plugin, HelpManager helpManager, HelpCategory category, HelpArticle article) {
        return ActionButton.create(
                plugin.messages().parse(article.title()),
                plugin.messages().parse(helpManager.message("dialog-click-to-read")),
                BUTTON_WIDTH,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player viewer) {
                        openArticle(plugin, helpManager, viewer, category, article);
                    }
                }, SINGLE_USE));
    }
}