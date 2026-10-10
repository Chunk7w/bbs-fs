package mchorse.bbs_mod.ui.film.utils.shader;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.iris.IrisShaderMenu;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderMenu;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Entry point of the curve picker: snapshots the shader pack's options menu, if there is one, and
 * opens {@link UICurvePicker} with it and the world's curves.
 *
 * <p><b>Iris isolation:</b> this class references no {@code net.irisshaders.*} type directly; it only
 * reaches Iris through {@link IrisShaderMenu} <i>after</i> the {@code isModLoaded("iris")} gate. That
 * keeps {@link IrisShaderMenu} (and Iris itself) unloaded when Iris is absent, and the picker then
 * shows the world's curves only.</p>
 */
public final class ShaderCurvePicker
{
    private ShaderCurvePicker()
    {}

    public static void open(UIContext context, List<UICurvePicker.WorldCurve> world, Collection<String> existing, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        ShaderMenu menu = null;
        Map<String, String> lang = Collections.emptyMap();

        if (FabricLoader.getInstance().isModLoaded("iris"))
        {
            try
            {
                menu = IrisShaderMenu.buildShaderMenu();

                if (menu != null)
                {
                    lang = IrisShaderMenu.getShadersRawLanguageMap(BBSModClient.getLanguageKey());
                }
            }
            catch (LinkageError e)
            {
                /* An Iris build whose option-menu API differs from the one we compiled against: no
                 * menu to mirror, so every curvable option goes on the "not in the menu" page */
                menu = flatMenu();
                lang = Collections.emptyMap();
            }
        }

        UICurvePicker picker = new UICurvePicker(world, menu, lang, existing, onAdd, onRemove);

        UIOverlay.addOverlay(context, picker, picker.preferredWidth(), picker.preferredHeight());
    }

    private static ShaderMenu flatMenu()
    {
        if (ShaderCurves.variableMap.isEmpty())
        {
            return null;
        }

        ShaderMenu.Screen main = new ShaderMenu.Screen(ShaderMenu.MAIN_SCREEN, 2, Collections.emptyList());

        return new ShaderMenu("", main, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), new HashSet<>(ShaderCurves.variableMap.keySet()));
    }
}
