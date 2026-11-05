package fr.darkvodou.facets.speed;

import fr.darkvodou.facets.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /speed [speed]: sets the flight speed of a flying player, else their walking speed.
 * 1 is the usual speed, 10 the fastest, 0.5 half the usual one. Without a speed, back to 1.
 */
public final class SpeedCommand implements TabExecutor {

    public static final float MAX = 10;
    // Speeds of Minecraft for a player who never changed them
    static final float FLY_BASE = 0.1f;
    static final float WALK_BASE = 0.2f;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        float speed = 1;
        if (args.length >= 1) {
            try {
                speed = Float.parseFloat(args[0].replace(',', '.'));
            } catch (NumberFormatException e) {
                Messages.send(player, "speed.invalid-number", "value", args[0]);
                return true;
            }
            if (!(speed >= 0 && speed <= MAX)) {
                Messages.send(player, "speed.range", "max", (int) MAX);
                return true;
            }
        }
        String shown = format(speed);
        if (player.isFlying()) {
            player.setFlySpeed(toMinecraft(speed, FLY_BASE));
            Messages.send(player, "speed.fly", "speed", shown);
        } else {
            player.setWalkSpeed(toMinecraft(speed, WALK_BASE));
            Messages.send(player, "speed.walk", "speed", shown);
        }
        return true;
    }

    /**
     * @param speed from 0 to {@link #MAX}, 1 being the usual speed
     * @param base  the usual speed in Minecraft's scale
     * @return the speed in Minecraft's scale (0 to 1): up to 1, a share of the usual speed; past 1, evenly up to
     * Minecraft's fastest
     */
    static float toMinecraft(float speed, float base) {
        if (speed <= 1) {
            return base * speed;
        }
        return Math.min(1, base + (speed - 1) / (MAX - 1) * (1 - base));
    }

    private static String format(float speed) {
        return speed == (int) speed ? Integer.toString((int) speed) : Float.toString(speed);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String last = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("0.5", "1", "2", "5", "10").filter(s -> s.startsWith(last)).toList();
    }
}
