package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.api.client.editor.TrackCategories;
import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Set;

/**
 * The headings a form's own tracks sit under in a timeline. The Form tab holds what used to be the
 * Form and Pose tabs; these keep a skeleton, a texture and an IK chain apart at a glance without
 * bringing back a Pose tab that was empty for every form but a model.
 */
public enum FormTrackGroup
{
    /* In the order the headings show. The rig bends the same skeleton the pose keys, so it follows it. */
    POSE("pose", Colors.RED),
    RIG("rig", 0xfb7185),
    TRANSFORM("transform", 0x4ade80),
    LOOK("look", 0xfb923c);

    private static final Set<String> RIG_PROPERTIES = Set.of("ik", "physics", "spline_ik", "wind", "shake");

    public final String id;
    public final IKey label;
    public final int color;

    FormTrackGroup(String id, int color)
    {
        this.id = id;
        this.label = L10n.lang("bbs.ui.film.form.sections." + id);
        this.color = color;
    }

    /** The heading of one part's tracks of this group, inside the part's own heading if it has one. */
    public UIKeyframeSheet.Section section(String path, UIKeyframeSheet.Section part)
    {
        return new UIKeyframeSheet.Section("form_section/" + path + "/" + this.id, this.label, this.color, part);
    }

    /** Null for a track another tab claims, an addon's for one: it is not the Form tab's to sort. */
    public static FormTrackGroup of(TrackId track)
    {
        if (TrackCategories.categoryOf(track, true) != TrackCategory.FORM)
        {
            return null;
        }

        switch (track.kind())
        {
            case BONE, BONE_CONSTRAINT:
                return POSE;
            case MATERIAL_TEXTURE, MATERIAL_PROP:
                return LOOK;
            case PROPERTY:
                break;
            default:
                return RIG;
        }

        String name = track.subject();

        if (name.equals("transform") || name.startsWith("transform_overlay"))
        {
            return TRANSFORM;
        }

        /* What moves the model: its pose and everything that plays or bends it. */
        if (name.equals(FormProperties.POSE_PROPERTY) || name.startsWith("pose_overlay") || name.equals("shape_keys")
            || name.equals("actions") || name.startsWith("cem_"))
        {
            return POSE;
        }

        if (RIG_PROPERTIES.contains(name) || name.startsWith("splines/"))
        {
            return RIG;
        }

        return LOOK;
    }
}
