package fr.darkvodou.facets;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFilesTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static final String BUNDLED = """
            loft:
              particles:
                # How far particles are shown
                view-distance: 128
                force: true
                # New option
                show-to-others: false
              set:
                default-net-count: 5
            """;

    @Test
    void addsMissingEntriesWithTheirComments() throws InvalidConfigurationException {
        YamlConfiguration current = yaml("""
                loft:
                  particles:
                    view-distance: 64
                    force: true
                """);
        List<String> added = ConfigFiles.merge(current, yaml(BUNDLED));

        assertEquals(List.of("loft.particles.show-to-others", "loft.set.default-net-count"), added);
        assertEquals(false, current.get("loft.particles.show-to-others"));
        assertEquals(5, current.getInt("loft.set.default-net-count"));
        assertEquals(List.of("New option"), current.getComments("loft.particles.show-to-others"));
    }

    @Test
    void keepsTheValuesAndCommentsOfTheUser() throws InvalidConfigurationException {
        YamlConfiguration current = yaml("""
                loft:
                  particles:
                    # My comment
                    view-distance: 64
                """);
        ConfigFiles.merge(current, yaml(BUNDLED));

        assertEquals(64, current.getInt("loft.particles.view-distance"));
        assertEquals(List.of("My comment"), current.getComments("loft.particles.view-distance"));
    }

    @Test
    void addsNothingToAnUpToDateFile() throws InvalidConfigurationException {
        assertTrue(ConfigFiles.merge(yaml(BUNDLED), yaml(BUNDLED)).isEmpty());
    }

    @Test
    void bundledFilesAreValid() throws Exception {
        for (String name : List.of("config.yml", "messages.yml")) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                assertFalse(yaml.getKeys(true).isEmpty(), name);
            }
        }
    }
}
