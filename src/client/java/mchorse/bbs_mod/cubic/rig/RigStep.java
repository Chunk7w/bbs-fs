package mchorse.bbs_mod.cubic.rig;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.shake.ModelShakeRuntime;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueModelDebug;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The rig's steps of a model's frame, in the one order they run. The shake is part of the pose: it
 * layers into the channels before their snapshot, so every step after it and every reader of the
 * channels sees the bones where they are drawn. The others run over the channels and write only the
 * bones' evaluated orientation and offset, each starting from what the step before it left.
 *
 * <p>Every pass of a frame runs its own set of them (the sets below), always in this order. The model
 * renderer is the one place that runs them; what a pass adds on top (a world frame or none, IK in the
 * shadow's sample only for a spline) stays written at its call there.</p>
 *
 * <p>Each step is also the one description of its kind of rig: the form properties a timeline keys it
 * by ({@link #tracks}), whether a form has anything of it set up, its debug overlay. The lists that
 * used to name them one by one (the Rig heading, the track looks, which tracks a form offers, F5) are
 * read from here.</p>
 */
public enum RigStep
{
    /** Bone shake, an additive layer of the pose: the channels phase. */
    SHAKE(new Track("shake", Icons.EXCHANGE, 0x159e64)),
    /** IK chains. */
    IK(new Track("ik", Icons.IK, Colors.WHITE)),
    /** The addons' pose hook ({@code FormPoseEvents.MODEL_POSE}): after IK, before the built-in physics. */
    HOOK,
    /** Chain physics, the one step with a memory over time. The wind acts on its chains, so it comes with them. */
    PHYSICS(new Track("physics", Icons.PHYSICS, 0x9d6cff), new Track("wind", Icons.PARTICLE, Colors.ORANGE)),
    /** Spline IK, solved in its root's parent frame after the physics; it may move the whole model. */
    SPLINE(new Track("spline_ik", Icons.GRAPH, Colors.BLUE)),
    /** Bone limits, the last word on a bone's rotation. Keyed per bone, not by a form property. */
    LIMITS;

    /** The form properties a timeline keys the step by, one track each, as they show there. */
    public final List<Track> tracks;

    RigStep(Track... tracks)
    {
        this.tracks = List.of(tracks);
    }

    /* The sets are enum sets: they iterate in the frame's order whatever order they were written in. */

    public static final Set<RigStep> ALL = Collections.unmodifiableSet(EnumSet.allOf(RigStep.class));

    /** The channels phase: what layers into the pose before its snapshot. */
    public static final Set<RigStep> CHANNELS = steps(SHAKE);

    /** The render: every step over the channels. */
    public static final Set<RigStep> RENDER = steps(IK, HOOK, PHYSICS, SPLINE, LIMITS);

    /**
     * The matrix readers (anchors, the gizmo, trackers, the shadow): no simulation and no limits.
     * The gizmo probes this pass with nudged channels, so it has to stay stateless.
     */
    public static final Set<RigStep> READERS = steps(IK, HOOK, SPLINE);

    /** The IK bake: the chains alone, it keys only what they solve. */
    public static final Set<RigStep> BAKE = steps(IK);

    private static Set<RigStep> steps(RigStep first, RigStep... rest)
    {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }

    /** The step a form property's track belongs to, or null for a property that isn't the rig's. */
    public static RigStep ofTrack(String property)
    {
        for (RigStep step : values())
        {
            for (Track track : step.tracks)
            {
                if (track.id().equals(property))
                {
                    return step;
                }
            }
        }

        return null;
    }

    /**
     * Whether the form has anything of this step set up: a chain, a shaking bone, a spline, limits.
     * What a timeline offers the step's tracks by — the setup, not its animated weight.
     */
    public boolean isUsed(ModelForm form)
    {
        return switch (this)
        {
            case SHAKE -> anyBone(form, FormBone::hasShake);
            case IK -> anyBone(form, FormBone::hasChain);
            case HOOK -> false;
            case PHYSICS -> anyBone(form, FormBone::hasPhysicsChain);
            case SPLINE -> !form.splines.getAll().isEmpty();
            case LIMITS -> anyBone(form, (bone) -> !bone.constraints.get().isDefault());
        };
    }

    private static boolean anyBone(ModelForm form, Predicate<FormBone> test)
    {
        for (BaseValue value : form.bones.getAll())
        {
            if (value instanceof FormBone bone && test.test(bone))
            {
                return true;
            }
        }

        return false;
    }

    /** The step's debug overlay setting, or null for a step that draws none. */
    public ValueModelDebug debug()
    {
        return switch (this)
        {
            case IK -> BBSSettings.ikDebug;
            case PHYSICS -> BBSSettings.physicsDebug;
            default -> null;
        };
    }

    /**
     * Reports which of the form's bones this step has a hand on and how (see {@link RigOwners}).
     * The addons' hook reports nothing: what a listener does to the pose is its own business.
     */
    public void claim(IModel model, ModelForm form, RigOwners owners)
    {
        switch (this)
        {
            case SHAKE -> ModelShakeRuntime.claim(model, form, owners);
            case IK -> ModelIKRuntime.claim(model, form, owners);
            case HOOK -> {}
            case PHYSICS -> ModelPhysicsRuntime.claim(model, form, owners);
            case SPLINE -> ModelSplineRuntime.claim(model, form, owners);
            case LIMITS -> ModelConstraintsRuntime.claim(model, form, owners);
        }
    }

    /**
     * A transform gesture began. The physics snapshots its simulation, so a rejected gesture can
     * put back what the drag drove, and the IK solve dump (when compiled on) opens for the drag.
     */
    public static void gestureStarted()
    {
        ModelPhysicsRuntime.checkpoint();
        ModelIKRuntime.logGesture(true);
    }

    /** The gesture is over, accepted or rejected: the snapshot is forgotten. */
    public static void gestureEnded()
    {
        ModelIKRuntime.logGesture(false);
        ModelPhysicsRuntime.dropCheckpoint();
    }

    /** The gesture was rejected and the pose is back where it began: the simulation goes back too. */
    public static void gestureRejected()
    {
        ModelPhysicsRuntime.rewindToCheckpoint();
    }

    /** A form property a step is keyed by, under its own name, and how its track looks in a timeline. */
    public record Track(String id, Icon icon, int color)
    {
        /** What the track is called in a timeline. */
        public IKey label()
        {
            return L10n.lang("bbs.ui.timeline.properties." + this.id);
        }
    }
}
