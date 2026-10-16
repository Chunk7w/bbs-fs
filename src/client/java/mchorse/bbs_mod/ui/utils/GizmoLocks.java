package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;

import java.util.Collection;
import java.util.List;

/**
 * Where the gizmo locks ({@link mchorse.bbs_mod.utils.pose.TransformLocks}) of the bones an editor
 * moves come from. Several bones merge: an axis any of them locks stays still for the whole
 * selection, since one gesture moves them all.
 */
public final class GizmoLocks
{
    private GizmoLocks()
    {
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
                locks |= formBone.gizmoLocks.get();
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

    /** The gizmo's handles for these locks when it is drawn in {@code space}. */
    public static Gizmo.HandleMask mask(int locks, TransformSpace space)
    {
        return Gizmo.HandleMask.ALL.locked(locks, space == TransformSpace.LOCAL);
    }
}
