package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.film.utils.shader.ShaderCurvePicker;
import mchorse.bbs_mod.ui.film.utils.shader.UICurvePicker;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class UICurveClip extends UIClip<CurveClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UICurveClip(CurveClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    /** The curves of the world itself, for the picker; they work with shaders off too. */
    private static List<UICurvePicker.WorldCurve> worldCurves()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<UICurvePicker.WorldCurve> curves = new ArrayList<>();

        curves.add(new UICurvePicker.WorldCurve(ShaderCurves.SUN_ROTATION, UIKeys.CAMERA_PANELS_CURVES_SUN_ROTATION, Icons.SUN)
            .value(() -> mc.world == null ? null : (mc.world.getTimeOfDay() % 24000L) / 1000D));
        curves.add(new UICurvePicker.WorldCurve(ShaderCurves.SUN_HORIZONTAL_ROTATION, UIKeys.CAMERA_PANELS_CURVES_SUN_HORIZONTAL_ROTATION, Icons.ORBIT)
            .value(() -> (double) BBSRendering.getSunHorizontalRotation()));
        curves.add(new UICurvePicker.WorldCurve(ShaderCurves.WEATHER, UIKeys.CAMERA_PANELS_CURVES_WEATHER, Icons.DROP)
            .value(() -> mc.world == null ? null : (double) mc.world.getRainGradient(1F)));
        curves.add(new UICurvePicker.WorldCurve(ShaderCurves.BRIGHTNESS, UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS, Icons.LIGHT)
            .value(() -> mc.options.getGamma().getValue()));
        curves.add(new UICurvePicker.WorldCurve(CurveClip.CHROMA_SKY_COLOR, UIKeys.CAMERA_PANELS_CURVES_CHROMA_SKY_COLOR, Icons.COLOR)
            .color(() -> BBSSettings.chromaSkyColor.get()));

        return curves;
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("curve_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.icon(MenuVerb.ADD, () ->
            {
                List<String> existing = new ArrayList<>();

                for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
                {
                    existing.add(channel.getId());
                }

                ShaderCurvePicker.open(this.getContext(), worldCurves(), existing, (s) ->
                {
                    if (CurveClip.isColorChannelId(s))
                    {
                        this.clip.channels.addChannel(s, KeyframeFactories.COLOR);
                    }
                    else
                    {
                        this.clip.channels.addChannel(s, KeyframeFactories.DOUBLE);
                    }

                    this.fillData();
                }, (s) ->
                {
                    if (this.clip.channels.get(s) instanceof KeyframeChannel<?> channel)
                    {
                        this.clip.channels.removeChannel(channel);
                        this.fillData();
                    }
                });
            }).label(UIKeys.CAMERA_PANELS_CURVE_ADD);

            UIKeyframeSheet sheet = this.keyframes.view.getGraph().getSheet(this.getContext().mouseX, this.getContext().mouseY);

            menu.icon(MenuVerb.REMOVE, () ->
            {
                this.clip.channels.removeChannel(sheet.channel);
                this.fillData();
            }).label(UIKeys.CAMERA_PANELS_CURVE_REMOVE).enabled(sheet != null);
        });

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private void addKeyframeSheet(KeyframeChannel<?> channel)
    {
        int sheetColor = channel.getId().hashCode() & Colors.RGB;

        this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), IKey.constant(channel.getId()), sheetColor, channel, null));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:curve"), this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        KeyframeState state = this.keyframes.view.getSheets().isEmpty() ? null : this.keyframes.view.cacheState();
        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            this.addKeyframeSheet(channel);
        }
        if (state != null) this.keyframes.view.applyState(state);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("curve"))
        {
            this.editor.embedView(this.keyframes);
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "curve");
        }

        super.collectUndoData(data);
    }
}
