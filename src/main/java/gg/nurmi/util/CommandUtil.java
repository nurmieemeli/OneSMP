package gg.nurmi.util;

import gg.nurmi.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommandUtil {

    private CommandUtil() {
    }

    public static BigDecimal parseAmount(MessageService messages, CommandSender sender, String input) {
        try {
            return new BigDecimal(input).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            messages.send(sender, "general.invalid-number", Placeholder.unparsed("input", input));
            return null;
        }
    }
}
