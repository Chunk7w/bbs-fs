package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.Axis;

import java.util.Collection;
import java.util.List;

/**
 * The rotation channels the gizmo leaves where they are on the bones an editor turns: the locks of
 * the bones' limits ({@link BoneConstraint}), the same ones the IK solve and the final pose keep, so
 * a bone's freedom is set in one place for the hand too. XYZ bits, X lowest; zero locks nothing.
 * Several bones merge: one gesture turns them all, so an axis any of them locks stays still for the
 * whole selection. Typed values and keyframes stay free, as with Blender's transform locks.
 */
public final class GizmoLocks
{
    /** Every rotation channel locked: the bone does not turn by hand at all. */
    public static final int ALL = 0b111;

    private GizmoLocks()
    {}

    public static int of(BoneConstraint constraint)
    {
        if (constraint == null)
        {
            return 0;
        }

        return (constraint.lockX ? 1 : 0) | (constraint.lockY ? 2 : 0) | (constraint.lockZ ? 4 : 0);
    }

    public static int of(ModelForm form, Collection<String> bones)
    {
        int locks = 0;

        if (form == null || bones == null)
        {
            return locks;
        }

        for (String bone : bones)
        {
            FormBone formBone = form.bones.getBone(bone);

            if (formBone != null)
            {
                locks |= of(formBone.constraints.get());
            }
        }

        return locks;
    }

    /**
     * The locks behind a keyframe editor of the film or the animation states: the bones picked in
     * a pose track's list, or a bone track's bone together with the bones of the other bone tracks
     * whose selected keyframes the same gesture moves.
     */
    public static int of(Form root, UIKeyframeEditor editor)
    {
        UIKeyframeSheet sheet = editor == null || editor.editor == null ? null : editor.editor.getSheet();

        if (root == null || sheet == null)
        {
            return 0;
        }

        if (editor.editor instanceof UIPoseKeyframeFactory pose)
        {
            BaseValueBasic property = FormUtils.getProperty(root, sheet.id);
            Form owner = property == null ? null : FormUtils.getForm(property);

            return owner instanceof ModelForm model ? of(model, pose.poseEditor.groups.list.getCurrent()) : 0;
        }

        int locks = ofBoneTrack(root, sheet.id);

        for (UIKeyframeSheet selected : editor.view.getOperationSheets())
        {
            if (selected != sheet && selected.selection.hasAny())
            {
                locks |= ofBoneTrack(root, selected.id);
            }
        }

        return locks;
    }

    private static int ofBoneTrack(Form root, String id)
    {
        TrackId track = TrackId.parse(id, TrackKind.BONE);
        Form form = track == null ? null : FormUtils.getForm(root, track.formPath());

        return form instanceof ModelForm model ? of(model, List.of(track.subject())) : 0;
    }

    public static boolean isLocked(int locks, Axis axis)
    {
        return axis != null && (locks & 1 << axis.ordinal()) != 0;
    }

    /** The one channel these locks leave free, or null when none or more than one is. */
    public static Axis onlyFree(int locks)
    {
        Axis free = null;

        for (Axis axis : Axis.values())
        {
            if (!isLocked(locks, axis))
            {
                if (free != null)
                {
                    return null;
                }

                free = axis;
            }
        }

        return free;
    }
}
