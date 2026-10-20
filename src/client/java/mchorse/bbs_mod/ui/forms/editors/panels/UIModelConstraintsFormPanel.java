package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigStep;
import mchorse.bbs_mod.cubic.constraints.BoneConstraint;
import mchorse.bbs_mod.cubic.constraints.BoneConstraintsIO;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.utils.UIBoneConstraintFields;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.bones.BoneRoles;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UIModelConstraintsFormPanel extends UIBoneListFormPanel
{
    public UIBoneConstraintFields fields;
    public UIButton applyToChildren;

    public UIModelConstraintsFormPanel(UIForm editor)
    {
        super(editor);

        this.bonePresets(ModelConstraintsManager.INSTANCE, "_CopyModelConstraints",
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_COPY,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_PASTE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_RESET,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_SAVE,
            UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_CONTEXT_NAME,
            this::toPresetData, this::applyPresetData
        );

        this.fields = new UIBoneConstraintFields(this::currentConstraint, this::editConstraint, this::updateFields);
        this.applyToChildren = new UIButton(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_APPLY_TO_CHILDREN, (b) -> this.applySelectedToChildren());

        UISection params = this.section(UIKeys.FORMS_EDITORS_MODEL_CONSTRAINTS_SETTINGS, "constraints.settings", true);

        params.fields.add(this.fields.rows());
        params.fields.add(this.applyToChildren.marginTop(UIConstants.SECTION_GAP));

        this.options.add(
            this.bonesSearch,
            params
        );
    }

    /** The selected bone's constraint as stored, or the neutral default when never touched. */
    private BoneConstraint currentConstraint()
    {
        if (this.form != null && !this.selectedBone.isEmpty())
        {
            FormBone bone = this.form.bones.getBone(this.selectedBone);

            if (bone != null)
            {
                return bone.constraints.get();
            }
        }

        return BoneConstraint.DEFAULT;
    }

    /** Edits the selected bone's constraint as a value change (one undo entry, one notification). */
    private void editConstraint(Consumer<BoneConstraint> edit)
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        FormBone bone = this.form.bones.getOrCreate(this.selectedBone);
        BoneConstraint constraint = bone.constraints.get().copy();

        edit.accept(constraint);
        bone.constraints.set(constraint);
    }

    /** A constrained bone simply reads at full strength — constraints are all this tab sets. */
    @Override
    protected void describeBones(BoneRoles roles)
    {
        roles.touch(this.owners(), RigStep.LIMITS);
    }

    @Override
    protected void updateFields()
    {
        if (this.fields == null)
        {
            return;
        }

        this.fields.fill(this.currentConstraint(), this.inIKChain());
        this.options.resize();
        this.updateFieldsEnabled();
    }

    /** Whether an IK chain runs through the selected bone: the stiffness is the solve's alone. */
    private boolean inIKChain()
    {
        for (RigClaim claim : this.owners().on(this.selectedBone))
        {
            if (claim.step() == RigStep.IK && claim.inChain())
            {
                return true;
            }
        }

        return false;
    }

    private void updateFieldsEnabled()
    {
        boolean panelEnabled = this.bones.isEnabled();
        boolean active = panelEnabled && !this.selectedBone.isEmpty();
        boolean hasChildren = active && !this.getDescendantBones(this.selectedBone).isEmpty();

        this.applyToChildren.setEnabled(hasChildren);
        this.fields.setEnabled(active, this.currentConstraint());
    }

    private void applySelectedToChildren()
    {
        if (this.form == null || this.selectedBone.isEmpty())
        {
            return;
        }

        List<String> descendants = this.getDescendantBones(this.selectedBone);

        if (descendants.isEmpty())
        {
            return;
        }

        BoneConstraint constraint = this.currentConstraint();

        for (String child : descendants)
        {
            this.form.bones.getOrCreate(child).constraints.set(constraint.copy());
        }
    }

    private List<String> getDescendantBones(String bone)
    {
        if (bone == null || bone.isEmpty() || this.modelInstance == null || this.modelInstance.model == null)
        {
            return Collections.emptyList();
        }

        IModel model = this.modelInstance.model;

        List<String> descendants = new ArrayList<>(model.getAllChildrenKeys(bone));

        if (!this.availableBones.isEmpty())
        {
            descendants.removeIf((id) -> !this.availableBones.contains(id));
        }

        return descendants;
    }

    @Override
    protected void setElementsEnabled(boolean enabled)
    {
        this.bonesSearch.setEnabled(enabled);
        this.bones.setEnabled(enabled);
        this.applyToChildren.setEnabled(enabled);
        this.updateFieldsEnabled();
    }

    protected MapType toPresetData()
    {
        return this.form == null ? new MapType() : BoneConstraintsIO.write(this.form.bones);
    }

    protected void applyPresetData(MapType map)
    {
        if (this.form == null)
        {
            return;
        }

        BoneConstraintsIO.read(map, this.form.bones, true);

        this.updateFields();
    }

}
