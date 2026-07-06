package gg.nurmi.util;

import gg.nurmi.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommandUtil {

    private CommandUtil() {
    }

    /** Parses a positive currency amount, sending an error message and returning null on failure. */
    public static BigDecimal parseAmount(MessageService messages, CommandSender sender, String input) {
        try {
            BigDecimal amount = new BigDecimal(input).setScale(2, RoundingMode.HALF_UP);
            return amount;
        } catch (NumberFormatException ex) {
            messages.send(sender, "general.invalid-number", Placeholder.unparsed("input", input));
            return null;
        }
    }
}
