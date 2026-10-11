package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSModClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The editor's way to the shader pack Iris has loaded: which pack is on, its menu and its options.
 * Free of Iris types: it reaches Iris through {@link IrisShaderMenu} and {@link IrisShaderPacks}
 * only once {@link #IRIS} says Iris is there, so the UI can use it with Iris missing.
 */
public final class ShaderOptions
{
    public static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    /**
     * Counts the pack's loads, switching it off, and its pipeline builds: after any of them the
     * pack may have other options, and other ones may be animated
     */
    private static volatile int revision;

    /**
     * Option values set without a reload since the pack was loaded: Iris only has them in its
     * option queue, and its own menu reads the values the pack was loaded with
     */
    private static final Map<String, String> live = new ConcurrentHashMap<>();

    private ShaderOptions()
    {}

    public static int revision()
    {
        return revision;
    }

    /** Iris started loading a pack, or switched shaders off */
    static void packLoading()
    {
        live.clear();
        changed();
    }

    /** What the loaded pack has may have changed: the pack finished loading, or its pipeline was built */
    public static void changed()
    {
        revision += 1;
    }

    /** The value an option was set to without a reload, or null */
    public static String getLive(String optionId)
    {
        return live.get(optionId);
    }

    /** The loaded pack's menu, or null with Iris missing or no pack on */
    public static ShaderMenu menu()
    {
        return IRIS ? IrisShaderMenu.buildShaderMenu() : null;
    }

    /** The loaded pack's own translations in the game's language */
    public static Map<String, String> lang()
    {
        return IRIS ? IrisShaderMenu.getShadersRawLanguageMap(BBSModClient.getLanguageKey()) : Collections.emptyMap();
    }

    /**
     * Sets an animatable option without a reload: its uniform takes the value at once, the pack's
     * next load keeps it, and the pack's settings file has it for the next game.
     */
    public static void setLive(String optionId, String value) throws IOException
    {
        if (!IRIS)
        {
            return;
        }

        live.put(optionId, value);
        IrisShaderMenu.queue(optionId, value);
        IrisShaderMenu.save(optionId, value);
    }

    /** Sets the options and reloads the pack once to apply them */
    public static void apply(Map<String, String> values) throws IOException
    {
        if (IRIS)
        {
            IrisShaderMenu.apply(values);
        }
    }

    /** Name of the pack that's on, or an empty string */
    public static String currentPack()
    {
        return IRIS ? IrisShaderPacks.current() : "";
    }
}
