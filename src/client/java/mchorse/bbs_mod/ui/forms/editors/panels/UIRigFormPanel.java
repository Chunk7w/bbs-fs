package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigHandles;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigPresets;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueModelDebug;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigIKSection;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigLimitsSection;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigPhysicsSection;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigSection;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigShakeSection;
import mchorse.bbs_mod.ui.forms.editors.panels.rig.UIRigSplineSection;
import mchorse.bbs_mod.ui.forms.editors.utils.UIDebugOverlayContextMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.utils.SplineFormTool;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UISplinePointsEditor;
import mchorse.bbs_mod.ui.utils.bones.BoneRole;
import mchorse.bbs_mod.ui.utils.bones.BoneRoles;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu;
import mchorse.bbs_mod.utils.pose.ModelRigManager;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The rig tab: everything that bends a model past its pose, by bone. Over one tree of the bones,
 * the selected bone shows each step of the rig it takes part in, in the order the frame runs them —
 * shake, IK, physics, spline IK, limits. A chain shows on any bone of it, and on what it reaches
 * for, and is edited from there; the tree marks every bone with what each step has on it, a column
 * a step. One preset holds the whole rig.
 */
public class UIRigFormPanel extends UIBoneListFormPanel implements SplineFormTool
{
    private static final IKey ROLE = IKey.constant("%s: %s");

    /** The marks of the tree, per step and what the bone is to the setting, named for the step. */
    private static final Map<RigStep, Map<RigClaim.Part, BoneRole>> ROLES = new EnumMap<>(RigStep.class);

    static
    {
        role(RigStep.SHAKE, RigClaim.Part.HOME, BoneRole.CHAIN, null);
        role(RigStep.IK, RigClaim.Part.HOME, BoneRole.CHAIN, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_TIP);
        role(RigStep.IK, RigClaim.Part.LINK, BoneRole.LINK, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_LINK);
        role(RigStep.IK, RigClaim.Part.TARGET, BoneRole.TARGET, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_TARGET);
        role(RigStep.IK, RigClaim.Part.POLE, BoneRole.POLE, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_POLE);
        role(RigStep.PHYSICS, RigClaim.Part.HOME, BoneRole.CHAIN, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_ROOT);
        role(RigStep.PHYSICS, RigClaim.Part.LINK, BoneRole.LINK, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_LINK);
        role(RigStep.PHYSICS, RigClaim.Part.TARGET, BoneRole.TARGET, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_ATTACH);
        role(RigStep.SPLINE, RigClaim.Part.HOME, BoneRole.CHAIN, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_TIP);
        role(RigStep.SPLINE, RigClaim.Part.LINK, BoneRole.LINK, UIKeys.FORMS_EDITORS_MODEL_RIG_ROLE_LINK);
        role(RigStep.LIMITS, RigClaim.Part.HOME, BoneRole.LIMIT, null);
    }

    private static void role(RigStep step, RigClaim.Part part, BoneRole base, IKey name)
    {
        IKey label = name == null ? step.title() : ROLE.format(step.title(), name);

        ROLES.computeIfAbsent(step, (s) -> new EnumMap<>(RigClaim.Part.class)).put(part, base.named(label));
    }

    public UIToggle debug;
    public UIToggle boneController;

    public UIRigShakeSection shake;
    public UIRigIKSection ik;
    public UIRigPhysicsSection physics;
    public UIRigSplineSection spline;
    public UIRigLimitsSection limits;

    private final List<UIRigSection> sections = new ArrayList<>();

    /** Who has a hand on which bone, as of the last refill of the fields. */
    private RigOwners owners = new RigOwners();

    public UIRigFormPanel(UIForm editor)
    {
        super(editor);

        this.bones.context(() -> new UIDataContextMenu(ModelRigManager.INSTANCE, this.presetGroup, this::toPresetData, this::applyPresetData)
            .tooltips("_CopyModelRig",
                UIKeys.FORMS_EDITORS_MODEL_RIG_CONTEXT_COPY,
                UIKeys.FORMS_EDITORS_MODEL_RIG_CONTEXT_PASTE,
                UIKeys.FORMS_EDITORS_MODEL_RIG_CONTEXT_RESET,
                UIKeys.FORMS_EDITORS_MODEL_RIG_CONTEXT_SAVE,
                UIKeys.FORMS_EDITORS_MODEL_RIG_CONTEXT_NAME)
            .icons(UIRigFormPanel::presetIcons));

        this.debug = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_RIG_DEBUG, (b) -> setDebug(b.getValue()));

        UIIcon looks = new UIIcon(Icons.GEAR, (b) -> this.openDebugLooks());

        looks.tooltip(UIKeys.MODEL_DEBUG_CONFIGURE);
        looks.wh(UIConstants.ICON_SIZE, UIConstants.TOGGLE_HEIGHT);

        UIElement debugRow = new UIElement();

        debugRow.row(0).preferred(0).height(UIConstants.TOGGLE_HEIGHT);
        debugRow.add(this.debug, looks);

        this.boneController = new UIToggle(UIKeys.FORMS_EDITORS_MODEL_IK_BONE_CONTROLLER,
            (b) -> this.editBone((bone) -> bone.boneController.set(b.getValue())));
        this.boneController.h(UIConstants.CONTROL_HEIGHT);
        this.boneController.tooltip(UIKeys.FORMS_EDITORS_MODEL_IK_BONE_CONTROLLER_TOOLTIP);
        this.resetBone(this.boneController, (bone) -> bone.boneController);

        this.shake = this.remember(new UIRigShakeSection(this), "rig.shake", true);
        this.ik = this.remember(new UIRigIKSection(this), "rig.ik", true);
        this.physics = this.remember(new UIRigPhysicsSection(this), "rig.physics", true);
        this.spline = this.remember(new UIRigSplineSection(this), "rig.spline", true);
        this.limits = new UIRigLimitsSection(this);

        this.sections.add(this.shake);
        this.sections.add(this.ik);
        this.sections.add(this.physics);
        this.sections.add(this.spline);
        this.sections.add(this.limits);

        this.options.add(debugRow, this.bonesSearch, this.boneController);
        this.options.add(this.sections.toArray(new UIElement[0]));
    }

    private static List<Icon> presetIcons(MapType preset)
    {
        List<Icon> icons = new ArrayList<>();

        for (RigStep step : RigPresets.steps(preset))
        {
            icons.add(step.icon);
        }

        return icons;
    }

    /* The rig's debug overlays: one switch for all of them */

    private static void setDebug(boolean enabled)
    {
        for (RigStep step : RigStep.SYSTEMS)
        {
            if (step.debug() != null)
            {
                step.debug().enabled.set(enabled);
            }
        }
    }

    private static boolean isDebug()
    {
        for (RigStep step : RigStep.SYSTEMS)
        {
            if (step.debug() != null && step.debug().enabled.get())
            {
                return true;
            }
        }

        return false;
    }

    /** The look of each step's overlay, a menu of its own. */
    private void openDebugLooks()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            for (RigStep step : RigStep.SYSTEMS)
            {
                ValueModelDebug debug = step.debug();

                if (debug != null)
                {
                    menu.action(step.icon, step.title(), () -> this.getContext().replaceContextMenu(new UIDebugOverlayContextMenu(debug)));
                }
            }
        });
    }

    /* What the sections see of the panel */

    public ModelForm getForm()
    {
        return this.form;
    }

    public UIForm getEditor()
    {
        return this.editor;
    }

    public ModelInstance getModelInstance()
    {
        return this.modelInstance;
    }

    public IModel getModel()
    {
        return this.modelInstance == null ? null : this.modelInstance.model;
    }

    public String getSelectedBone()
    {
        return this.selectedBone;
    }

    public List<String> getAvailableBones()
    {
        return this.availableBones;
    }

    public FormBone getFormBone(String bone)
    {
        return this.form == null || bone == null || bone.isEmpty() ? null : this.form.bones.getBone(bone);
    }

    public RigOwners getOwners()
    {
        return this.owners;
    }

    /** The setting of the step the bone takes part in: the one it lives on, else a chain it is in, else one it is looked at by. */
    public RigClaim chainOf(RigStep step, String bone)
    {
        RigClaim best = null;

        for (RigClaim claim : this.owners.on(bone))
        {
            if (claim.step() == step && (best == null || claim.part().ordinal() < best.part().ordinal()))
            {
                best = claim;
            }
        }

        return best;
    }

    /** Edits the form as one change, for one undo, and refills the fields. */
    public void edit(Consumer<ModelForm> edit)
    {
        if (this.form == null)
        {
            return;
        }

        BaseValue.edit(this.form, IValueListener.FLAG_UNMERGEABLE, edit);
        this.updateFields();
    }

    /** Edits one bone of the form, creating its entry first. */
    public void editFormBone(String bone, Consumer<FormBone> edit)
    {
        if (this.form != null && bone != null && !bone.isEmpty())
        {
            edit.accept(this.form.bones.getOrCreate(bone));
        }
    }

    public UIBonePicker.Viewport viewportPicking()
    {
        return this.viewportBonePicking();
    }

    public <S extends UISection> S remember(S section, String id, boolean defaultExpanded)
    {
        return super.remember(section, id, defaultExpanded);
    }

    /** Moves the selection to the bone, as if it was clicked in the tree. */
    public void selectBoneInTree(String bone)
    {
        if (!this.pickBoneInList(bone))
        {
            this.updateFields();
        }
    }

    public void refresh()
    {
        this.updateFields();
    }

    /** A section's switch flipped: the step goes on or off the selected bone. */
    public void switchStep(UIRigSection section, boolean on)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            this.updateFields();

            return;
        }

        section.switched(this.selectedBone, on);
        this.updateFields();
    }

    /* The panel */

    @Override
    public void startEdit(ModelForm form)
    {
        this.spline.endPointEdit();
        this.debug.setValue(isDebug());

        super.startEdit(form);
    }

    @Override
    public void finishEdit()
    {
        this.spline.endPointEdit();
        RigHandles.hide(this.form);

        super.finishEdit();
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.boneController.setEnabled(enabled);

        for (UIRigSection section : this.sections)
        {
            section.setFieldsEnabled(enabled);
        }
    }

    @Override
    protected void updateFields()
    {
        if (this.limits == null)
        {
            return;
        }

        this.owners = RigOwners.of(this.getModel(), this.form);

        FormBone bone = this.selectedFormBone();
        boolean canEdit = this.bones.isEnabled() && !this.selectedBone.isEmpty();

        this.boneController.setEnabled(canEdit);
        this.boneController.setValue(bone != null && bone.boneController.get());

        for (UIRigSection section : this.sections)
        {
            section.refresh(this.selectedBone);
            section.setFieldsEnabled(canEdit);
        }

        this.options.resize();
    }

    /**
     * Every step's marks, a column a step in the frame's order from the left: the bone a setting
     * lives on, the links of its chain (and the line along the tree's branches), what it reaches
     * for. A bone with only a controller on it reads at full strength without a mark.
     */
    @Override
    protected void describeBones(BoneRoles roles)
    {
        RigOwners owners = this.owners();

        for (RigClaim claim : owners.getClaims())
        {
            Map<RigClaim.Part, BoneRole> marks = ROLES.get(claim.step());
            BoneRole role = marks == null ? null : marks.get(claim.part());

            if (role == null || (claim.part() == RigClaim.Part.POLE && !claim.on()) || (claim.step() == RigStep.LIMITS && !claim.on()))
            {
                continue;
            }

            int slot = RigStep.SYSTEMS.size() - 1 - RigStep.SYSTEMS.indexOf(claim.step());
            BoneRole shown = claim.on() ? role : role.off();

            if (claim.part() == RigClaim.Part.HOME && claim.chain().size() > 1)
            {
                BoneRole link = marks.get(RigClaim.Part.LINK);

                roles.chain(claim.chain(), claim.on() ? link : link.off(), slot);
            }

            if (claim.part() != RigClaim.Part.LINK)
            {
                roles.mark(claim.bone(), slot, shown);
            }
        }

        this.forEachFormBone((bone) ->
        {
            if (bone.boneController.get())
            {
                roles.touch(bone.getId());
            }
        });
    }

    @Override
    public void render(UIContext context)
    {
        /* The rig's handles show while the tab is drawn, debug overlay or not. */
        RigHandles.show(this.form);

        if (this.debug.getValue() != isDebug())
        {
            this.debug.setValue(isDebug());
        }

        super.render(context);
    }

    /* The rig preset: every step set up on the form */

    private MapType toPresetData()
    {
        return this.form == null ? new MapType() : RigPresets.write(this.form);
    }

    private void applyPresetData(MapType preset)
    {
        if (this.form == null)
        {
            return;
        }

        this.spline.endPointEdit();
        RigPresets.apply(this.form, preset);
        this.updateFields();
    }

    /* The spline's points in the viewport, see SplineFormTool */

    @Override
    public List<SplineSource> splineSources()
    {
        return this.form == null ? List.of() : new ArrayList<>(this.form.splines.getAllTyped());
    }

    @Override
    public SplineSource activeSpline()
    {
        return this.spline.chain();
    }

    @Override
    public UISplinePointsEditor pointEditor()
    {
        return this.spline.pointEditor;
    }

    @Override
    public void selectSpline(SplineSource source)
    {
        if (source instanceof SplineIK chain && !chain.getId().equals(this.spline.getChainId()))
        {
            this.spline.select(chain.getId(), "");
        }
    }

    @Override
    public UIPropTransform getGizmoTransform()
    {
        return this.spline.getGizmoTransform();
    }

    @Override
    public Matrix4f getGizmoOrigin(float transition, TransformSpace space)
    {
        return this.spline.getGizmoOrigin(transition);
    }
}
