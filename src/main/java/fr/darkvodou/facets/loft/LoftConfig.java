package fr.darkvodou.facets.loft;

import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Loft settings, read from the "loft" section of config.yml.
 */
public record LoftConfig(
        boolean shownByDefault,
        int refreshTicks,
        double viewDistance,
        boolean force,
        boolean showToOthers,
        int maxParticles,
        Style outline,
        double outlineStep,
        Style net,
        double netStep,
        double netSpacing,
        Style frameStart,
        Style point,
        int defaultNetCount
) {

    /**
     * A particle with its data, count and spread.
     */
    public record Style(Particle particle, @Nullable Object data, int count, double spread) {

        ParticleBuilder builder(boolean force) {
            return new ParticleBuilder(particle).count(count).offset(spread, spread, spread).extra(0).data(data).force(force);
        }
    }

    /**
     * @param warn receives a message for each invalid value, replaced by its default
     */
    public static LoftConfig load(FileConfiguration config, Consumer<String> warn) {
        ConfigurationSection particles = section(config, "loft.particles");
        ConfigurationSection set = section(config, "loft.set");
        return new LoftConfig(
                particles.getBoolean("shown-by-default", true),
                Math.max(1, particles.getInt("refresh-ticks", 10)),
                Math.max(1, particles.getDouble("view-distance", 256)),
                particles.getBoolean("force", true),
                particles.getBoolean("show-to-others", false),
                Math.max(1, particles.getInt("max-particles", 4000)),
                style(particles, "outline", Particle.FLAME, 1, 0, warn),
                positive(particles, "outline.step", 0.5, warn),
                style(particles, "net", Particle.FLAME, 1, 0, warn),
                positive(particles, "net.step", 1.0, warn),
                positive(particles, "net.spacing", 3.0, warn),
                style(particles, "frame-start", Particle.SOUL_FIRE_FLAME, 6, 0.2, warn),
                style(particles, "point", Particle.SOUL_FIRE_FLAME, 2, 0.08, warn),
                Math.max(1, set.getInt("default-net-count", 5))
        );
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : config.createSection(path);
    }

    private static double positive(ConfigurationSection section, String path, double def, Consumer<String> warn) {
        double value = section.getDouble(path, def);
        if (value <= 0) {
            warn.accept(section.getCurrentPath() + "." + path + " must be positive, using " + def);
            return def;
        }
        return value;
    }

    private static Style style(ConfigurationSection parent, String path, Particle defParticle, int defCount,
                               double defSpread, Consumer<String> warn) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            return new Style(defParticle, null, defCount, defSpread);
        }
        String where = section.getCurrentPath();
        int count = Math.max(1, section.getInt("count", defCount));
        double spread = Math.max(0, section.getDouble("spread", defSpread));

        Particle particle = particle(section.getString("particle"));
        if (particle == null) {
            warn.accept(where + ".particle: unknown particle '" + section.getString("particle") + "', using "
                    + defParticle.getKey().getKey());
            return new Style(defParticle, null, count, spread);
        }

        Class<?> dataType = particle.getDataType();
        Object data = null;
        if (dataType == Particle.DustOptions.class || dataType == Color.class) {
            Color color = color(section.getString("color", "#ff6a00"));
            if (color == null) {
                warn.accept(where + ".color: invalid color '" + section.getString("color") + "', using #ff6a00");
                color = Color.fromRGB(0xff6a00);
            }
            data = dataType == Color.class ? color : new Particle.DustOptions(color, (float) section.getDouble("size", 1.0));
        } else if (dataType != Void.class) {
            warn.accept(where + ".particle: " + particle.getKey().getKey() + " needs data that is not supported, using "
                    + defParticle.getKey().getKey());
            return new Style(defParticle, null, count, spread);
        }
        return new Style(particle, data, count, spread);
    }

    @Nullable
    private static Particle particle(@Nullable String name) {
        if (name == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.PARTICLE_TYPE.get(key);
    }

    @Nullable
    private static Color color(@Nullable String hex) {
        if (hex == null) {
            return null;
        }
        String digits = hex.trim().startsWith("#") ? hex.trim().substring(1) : hex.trim();
        if (digits.length() != 6) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(digits, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
