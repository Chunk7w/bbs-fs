package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A bone as it rests, before anything poses it: where it points, how long it is and how it is
 * turned. Rig steps that aim bones read it here instead of each working it out for itself — the
 * rest rotation bugs of October lived in the copies that had drifted apart.
 *
 * <p>Measures are the skeleton's own: a cubic model's pixels, as its pivots are authored, a BOBJ
 * armature's bind units. An axis is only ever read for its direction, so each step keeps the
 * zero length test it was written with; {@link #scale} turns an authored measure into model space.
 *
 * <p>The two skeletons disagree on a child that sits on the bone's own pivot: a cubic bone reports
 * the zero and leaves the step to skip it, a BOBJ bone looks further for a direction.
 */
public abstract class BonePassport
{
    private static final float EPS = 1.0e-6F;

    /** The passport of that model's skeleton, or null for a model no rig step can pose. */
    public static BonePassport of(IModel model)
    {
        if (model instanceof Model cubic)
        {
            return new Cubic(cubic);
        }

        if (model instanceof BOBJModel bobj)
        {
            return new Bobj(bobj);
        }

        return null;
    }

    /** Model units per authored unit: a cubic pixel is a sixteenth of a block, a BOBJ armature is authored in model units. */
    public abstract float scale();

    /**
     * Where the bone points at rest, heading down a chain: towards {@code child}; at the chain's end
     * ({@code child} null), on along the line the chain came in on from {@code parent}; with no
     * parent either, the way the bone leads by itself — its first child, else straight down. Null
     * when the bone is missing (or a cubic chain's named parent is). Unnormalised, authored measure.
     */
    public abstract Vector3f restAxisPastTip(String bone, String child, String parent);

    /**
     * Direction to rotation: the local rotation that swings a rest axis onto the world direction
     * {@code from → to}, as seen from the parent's world frame — the step a chain writer takes for
     * each bone. Normalises {@code restAxis} in place, for the twist a writer takes about it. Null
     * when either direction has no length, before or after the turn into the parent's frame.
     */
    public static Quaternionf aim(Vector3f restAxis, Vector3f from, Vector3f to, Quaternionf parentWorld)
    {
        Vector3f direction = new Vector3f(to).sub(from);

        if (restAxis.lengthSquared() < EPS * EPS || direction.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        restAxis.normalize();
        direction.normalize();
        new Quaternionf(parentWorld).invert().transform(direction);

        if (direction.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        return Matrices.fromToMirroredX(restAxis, direction.normalize());
    }

    private static final class Cubic extends BonePassport
    {
        private final Model model;

        private Cubic(Model model)
        {
            this.model = model;
        }

        @Override
        public float scale()
        {
            return 1F / 16F;
        }

        @Override
        public Vector3f restAxisPastTip(String bone, String child, String parent)
        {
            ModelGroup group = this.model.getGroup(bone);

            if (group == null)
            {
                return null;
            }

            ModelGroup next = child == null ? null : this.model.getGroup(child);

            if (next != null)
            {
                return new Vector3f(next.initial.translate).sub(group.initial.translate);
            }

            if (parent != null)
            {
                ModelGroup previous = this.model.getGroup(parent);

                return previous == null ? null : new Vector3f(group.initial.translate).sub(previous.initial.translate);
            }

            return leads(group);
        }

        /** The way a bone points by itself: at its first child, else straight down. */
        private static Vector3f leads(ModelGroup group)
        {
            if (group.children != null && !group.children.isEmpty())
            {
                return new Vector3f(group.children.get(0).initial.translate).sub(group.initial.translate);
            }

            return new Vector3f(0F, -1F, 0F);
        }
    }

    private static final class Bobj extends BonePassport
    {
        private final BOBJModel model;

        private Bobj(BOBJModel model)
        {
            this.model = model;
        }

        @Override
        public float scale()
        {
            return 1F;
        }

        @Override
        public Vector3f restAxisPastTip(String bone, String child, String parent)
        {
            BOBJBone b = this.model.getArmature().bones.get(bone);

            if (b == null)
            {
                return null;
            }

            BOBJBone next = child == null ? null : this.model.getArmature().bones.get(child);

            if (next != null)
            {
                Vector3f out = next.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            /* The bone's own bind offset is the line its parent leads into it. */
            if (parent != null)
            {
                Vector3f out = b.relBoneMat.getTranslation(new Vector3f());

                if (out.lengthSquared() > EPS * EPS)
                {
                    return out;
                }
            }

            return this.leads(b);
        }

        /** The way a bone points by itself: at the first child off its head, else straight down. */
        private Vector3f leads(BOBJBone bone)
        {
            for (BOBJBone candidate : this.model.getArmature().orderedBones)
            {
                if (candidate != null && candidate.parentBone == bone)
                {
                    Vector3f out = candidate.relBoneMat.getTranslation(new Vector3f());

                    if (out.lengthSquared() > EPS * EPS)
                    {
                        return out;
                    }
                }
            }

            return new Vector3f(0F, -1F, 0F);
        }
    }
}
