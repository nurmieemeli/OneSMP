package gg.nurmi.market;

import gg.nurmi.OneSMPPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;

// The search prompt, built with Paper's Dialog API (https://docs.papermc.io/paper/dev/dialogs/) instead of a chat/anvil text-input workaround.
public final class MarketSearchDialog {

    private static final String QUERY_KEY = "query";

    private MarketSearchDialog() {
    }

    public static void open(OneSMPPlugin plugin, MarketManager marketManager, Player player) {
        ActionButton searchButton = ActionButton.create(
                Component.text("Search"),
                Component.text("Search the market by item name"),
                150,
                DialogAction.customClick(
                        (response, audience) -> {
                            if (audience instanceof Player searcher) {
                                MarketActions.search(plugin, marketManager, searcher, response.getText(QUERY_KEY));
                            }
                        },
                        ClickCallback.Options.builder().uses(1).build()));

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Search the Market"))
                        .inputs(List.of(DialogInput.text(QUERY_KEY, Component.text("Item name")).build()))
                        .build())
                .type(DialogType.notice(searchButton)));

        player.showDialog(dialog);
    }
}
