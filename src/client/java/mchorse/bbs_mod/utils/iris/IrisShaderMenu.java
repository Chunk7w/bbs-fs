package mchorse.bbs_mod.utils.iris;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.LanguageMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.MergedBooleanOption;
import net.irisshaders.iris.shaderpack.option.MergedStringOption;
import net.irisshaders.iris.shaderpack.option.OptionSet;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElementScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.irisshaders.iris.shaderpack.option.values.OptionValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Reads the Iris options menu into a neutral snapshot for the shader panel of the curve clip, and
 * sets the pack's options for the panel. Only call after checking that Iris is loaded;
 * UI classes do not link Iris types. Adapted from qualet's BBS Curve Fixer (see
 * docs/third-party/curvefixer.md).
 */
public final class IrisShaderMenu
{
    private IrisShaderMenu()
    {}

    /**
     * Raw merged shader-pack translations (en_us fallback overlaid with {@code language}), without the
     * breadcrumb path expansion {@code IrisUtils.getShadersLanguageMap} applies. Exposes the original
     * keys: {@code option.<id>}, {@code screen.<id>}, {@code value.<id>.<val>}, {@code prefix.<id>},
     * {@code suffix.<id>}, etc. — used by the shader panel to label rows with their short names.
     */
    public static Map<String, String> getShadersRawLanguageMap(String language)
    {
        if (Iris.getCurrentPack().isPresent())
        {
            ShaderPack shaderPack = Iris.getCurrentPack().get();
            LanguageMap languageMap = shaderPack.getLanguageMap();
            Map<String, String> map = new HashMap<>();
            Map<String, String> fallback = languageMap.getTranslations("en_us");
            Map<String, String> target = languageMap.getTranslations(language);

            if (fallback != null)
            {
                map.putAll(fallback);
            }

            if (target != null)
            {
                map.putAll(target);
            }

            return map;
        }

        return Collections.emptyMap();
    }

    /**
     * Snapshot the currently loaded shader pack's options menu into the neutral {@link ShaderMenu}
     * model, with the animatable options ({@link ShaderCurves#getCurvable()}) no screen shows as
     * cells of their own.
     *
     * <p>Returns {@code null} when no pack is loaded. The snapshot is only valid for the pack it was
     * taken from: the shader panel takes a new one after every load.</p>
     */
    public static ShaderMenu buildShaderMenu()
    {
        Optional<ShaderPack> packOptional = Iris.getCurrentPack();

        if (packOptional.isEmpty())
        {
            return null;
        }

        ShaderPack pack = packOptional.get();
        OptionMenuContainer container = pack.getMenuContainer();
        Set<String> curvable = ShaderCurves.getCurvable();
        Set<String> menuOptions = new HashSet<>();

        ShaderMenu.Screen mainScreen = buildScreen(ShaderMenu.MAIN_SCREEN, container.mainScreen, curvable, menuOptions);
        Map<String, ShaderMenu.Screen> subScreens = new LinkedHashMap<>();

        for (Map.Entry<String, OptionMenuElementScreen> entry : container.subScreens.entrySet())
        {
            subScreens.put(entry.getKey(), buildScreen(entry.getKey(), entry.getValue(), curvable, menuOptions));
        }

        ShaderMenu menu = new ShaderMenu(packName(), mainScreen, subScreens);
        OptionSet options = pack.getShaderPackOptions().getOptionSet();
        OptionValues applied = pack.getShaderPackOptions().getOptionValues();

        for (String optionId : options.getBooleanOptions().keySet())
        {
            menu.values.put(optionId, Boolean.toString(applied.getBooleanValueOrDefault(optionId)));
        }

        for (String optionId : options.getStringOptions().keySet())
        {
            menu.values.put(optionId, applied.getStringValueOrDefault(optionId));
        }

        List<String> sliders = IrisUtils.getSliderProperties();

        for (String optionId : curvable)
        {
            ShaderMenu.Cell cell = menuOptions.contains(optionId) ? null : optionCell(options, applied, optionId, sliders.contains(optionId));

            if (cell != null)
            {
                menu.notInMenu.add(cell);
            }
        }

        container.getProfiles().forEach((name, profile) -> menu.profiles.put(name, new LinkedHashMap<>(profile.optionValues)));

        return menu;
    }

    /**
     * Puts an option's value in Iris' option queue: an animatable option's uniform takes it at
     * once ({@link QueueMap}), and the pack's next load applies it.
     */
    public static void queue(String optionId, String value)
    {
        Iris.getShaderPackOptionQueue().put(optionId, value);
    }

    /** Queues the values and reloads the pack once, so they apply. */
    public static void apply(Map<String, String> values) throws IOException
    {
        Iris.getShaderPackOptionQueue().putAll(values);
        Iris.reload();
    }

    /**
     * Writes an option's value into the pack's settings file the way Iris does after a load (a
     * value equal to the default is left out). Iris writes it only when it loads a pack, so a value
     * set without a reload would otherwise be gone with the game.
     */
    public static void save(String optionId, String value) throws IOException
    {
        Optional<ShaderPack> pack = Iris.getCurrentPack();

        if (pack.isEmpty())
        {
            return;
        }

        Path path = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
        Properties properties = new Properties();

        if (Files.exists(path))
        {
            try (InputStream in = Files.newInputStream(path))
            {
                properties.load(in);
            }
        }

        if (value.equals(defaultValue(pack.get().getShaderPackOptions().getOptionSet(), optionId)))
        {
            properties.remove(optionId);
        }
        else
        {
            properties.setProperty(optionId, value);
        }

        if (properties.isEmpty())
        {
            Files.deleteIfExists(path);

            return;
        }

        try (OutputStream out = Files.newOutputStream(path))
        {
            properties.store(out, null);
        }
    }

    private static String defaultValue(OptionSet options, String optionId)
    {
        MergedBooleanOption booleanOption = options.getBooleanOptions().get(optionId);

        if (booleanOption != null)
        {
            return Boolean.toString(booleanOption.getOption().getDefaultValue());
        }

        MergedStringOption stringOption = options.getStringOptions().get(optionId);

        return stringOption == null ? null : stringOption.getOption().getDefaultValue();
    }

    /** A cell for an animatable option no screen shows, built from the pack's own option */
    private static ShaderMenu.Cell optionCell(OptionSet options, OptionValues applied, String optionId, boolean slider)
    {
        MergedBooleanOption booleanOption = options.getBooleanOptions().get(optionId);

        if (booleanOption != null)
        {
            boolean current = applied.getBooleanValueOrDefault(optionId);
            String defaultValue = Boolean.toString(booleanOption.getOption().getDefaultValue());

            return ShaderMenu.Cell.option(optionId, slider, true, true, Boolean.toString(current), defaultValue, Arrays.asList("false", "true"), current ? 1 : 0);
        }

        MergedStringOption stringOption = options.getStringOptions().get(optionId);

        if (stringOption == null)
        {
            return null;
        }

        String current = applied.getStringValueOrDefault(optionId);
        List<String> allowed = new ArrayList<>(stringOption.getOption().getAllowedValues());

        return ShaderMenu.Cell.option(optionId, slider, false, true, current, stringOption.getOption().getDefaultValue(), allowed, allowed.indexOf(current));
    }

    private static String packName()
    {
        String name = Iris.getIrisConfig().getShaderPackName().orElse("");

        return name.toLowerCase().endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private static ShaderMenu.Screen buildScreen(String id, OptionMenuElementScreen screen, Set<String> curvable, Set<String> menuOptions)
    {
        List<ShaderMenu.Cell> cells = new ArrayList<>();

        for (OptionMenuElement element : screen.elements)
        {
            cells.add(buildCell(element, curvable, menuOptions));
        }

        return new ShaderMenu.Screen(id, screen.getColumnCount(), cells);
    }

    private static ShaderMenu.Cell buildCell(OptionMenuElement element, Set<String> curvable, Set<String> menuOptions)
    {
        if (element instanceof OptionMenuLinkElement link)
        {
            return ShaderMenu.Cell.link(link.targetScreenId);
        }
        else if (element instanceof OptionMenuProfileElement profile)
        {
            ProfileSet profiles = profile.profiles;
            ProfileSet.ProfileResult result = profiles.scan(profile.options, profile.getPendingOptionValues());
            String name = result.current.map((p) -> p.name).orElse(null);

            return ShaderMenu.Cell.profile(name, profiles.size());
        }
        else if (element instanceof OptionMenuOptionElement option)
        {
            String optionId = option.optionId;
            boolean curve = curvable.contains(optionId);
            OptionValues values = option.getAppliedOptionValues();

            menuOptions.add(optionId);

            if (element instanceof OptionMenuBooleanOptionElement booleanElement)
            {
                boolean current = values.getBooleanValueOrDefault(optionId);
                String defaultValue = Boolean.toString(booleanElement.option.getDefaultValue());

                return ShaderMenu.Cell.option(optionId, option.slider, true, curve, Boolean.toString(current), defaultValue, Arrays.asList("false", "true"), current ? 1 : 0);
            }
            else if (element instanceof OptionMenuStringOptionElement stringElement)
            {
                String current = values.getStringValueOrDefault(optionId);
                List<String> allowed = new ArrayList<>(stringElement.option.getAllowedValues());

                return ShaderMenu.Cell.option(optionId, option.slider, false, curve, current, stringElement.option.getDefaultValue(), allowed, allowed.indexOf(current));
            }

            /* Unknown option subtype — still record its presence in the menu set. */
            return ShaderMenu.Cell.option(optionId, option.slider, false, curve, null, null, Collections.emptyList(), -1);
        }

        return ShaderMenu.Cell.empty();
    }
}
