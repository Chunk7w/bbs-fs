package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;

import java.util.Set;

/**
 * Which form's rig handles show without the debug overlay: the controllers and the splines' points
 * of the form open on the rig tab, for as long as the tab is drawn, and which of its spline's points
 * are picked there. The tab says so every frame it draws, so a closed editor needs nothing to take
 * it back.
 */
public final class RigHandles
{
    private static ModelForm form;
    private static long frame = Long.MIN_VALUE;
    private static String spline = "";
    private static Set<String> points = Set.of();

    private RigHandles()
    {}

    /** The rig tab is drawn for this form in this frame, with these points of this spline picked. */
    public static void show(ModelForm form, String spline, Set<String> points)
    {
        RigHandles.form = form;
        RigHandles.frame = RenderFrame.getFrame();
        RigHandles.spline = spline;
        RigHandles.points = points;
    }

    /** The rig tab stopped showing this form. */
    public static void hide(ModelForm form)
    {
        if (RigHandles.form == form)
        {
            RigHandles.form = null;
        }
    }

    /**
     * Whether the form's handles show: the tab drew it in this frame or the one before (the world
     * renders before the interface does).
     */
    public static boolean shown(ModelForm form)
    {
        return form != null && form == RigHandles.form && RenderFrame.getFrame() - frame <= 1;
    }

    /** Whether the spline's point is picked on the rig tab drawn for the form. */
    public static boolean picked(ModelForm form, String spline, String point)
    {
        return shown(form) && RigHandles.spline.equals(spline) && RigHandles.points.contains(point);
    }
}
