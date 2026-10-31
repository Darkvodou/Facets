package fr.darkvodou.facets;

import com.fastasyncworldedit.core.configuration.Settings;

import java.util.List;
import java.util.Locale;

/**
 * FAWE only keeps the extents of the plugins it is told about, in its allowed-plugins setting.
 */
public final class FaweExtents {

    private FaweExtents() {
    }

    public static void allow(Class<?> extent) {
        List<String> allowed = Settings.settings().EXTENT.ALLOWED_PLUGINS;
        String name = extent.getName();
        if (allowed.stream().noneMatch(entry -> name.toLowerCase(Locale.ROOT).contains(entry.toLowerCase(Locale.ROOT)))) {
            allowed.add(name);
        }
    }
}
