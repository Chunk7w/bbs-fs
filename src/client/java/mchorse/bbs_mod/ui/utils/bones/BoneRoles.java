package mchorse.bbs_mod.ui.utils.bones;

import mchorse.bbs_mod.cubic.rig.RigClaim;
import mchorse.bbs_mod.cubic.rig.RigOwners;
import mchorse.bbs_mod.cubic.rig.RigStep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a host says about its bones, asked again every time the list draws (see
 * {@link UIBoneTreeList#roles}). The host only answers "what is on this bone"; the list turns
 * the answer into brightness (a bone with anything on it reads at full strength, the rest
 * quieter), role marks, and chains drawn along the tree's branches.
 */
public class BoneRoles
{
    private final Set<String> touched = new HashSet<>();
    private final Map<String, BoneRole[]> marks = new HashMap<>();
    private final Map<String, Link> links = new HashMap<>();

    /** A bone that has something on it but no mark of its own — a shaking bone, a posed one. */
    public void touch(String bone)
    {
        if (bone != null && !bone.isEmpty())
        {
            this.touched.add(bone);
        }
    }

    /**
     * Put a mark into a slot, {@code 0} being the rightmost. Every slot keeps its meaning down
     * the list, so the eye reads a column. A stronger mark already in the slot stays (see
     * {@link BoneRole#strength()}).
     */
    public void mark(String bone, int slot, BoneRole role)
    {
        if (bone == null || bone.isEmpty() || role == null)
        {
            return;
        }

        BoneRole[] row = this.marks.get(bone);

        if (row == null || row.length <= slot)
        {
            BoneRole[] grown = new BoneRole[slot + 1];

            if (row != null)
            {
                System.arraycopy(row, 0, grown, 0, row.length);
            }

            row = grown;
            this.marks.put(bone, row);
        }

        if (row[slot] == null || role.strength() > row[slot].strength())
        {
            row[slot] = role;
        }

        this.touched.add(bone);
    }

    /**
     * A chain, root to tip. Every bone of it gets {@code link} in slot 0 (unless something
     * stronger sits there, like the mark of the bone the chain lives on). In the tree the
     * chain is drawn along the branches between its bones instead, so a link mark only shows
     * where no branch can carry it: on the chain's first bone and in search results.
     */
    public void chain(List<String> bones, BoneRole link)
    {
        if (bones == null || bones.isEmpty())
        {
            return;
        }

        Set<String> chain = new HashSet<>(bones);

        for (int i = 0; i < bones.size(); i++)
        {
            String bone = bones.get(i);

            this.mark(bone, 0, link);

            if (i > 0)
            {
                Link existing = this.links.get(bone);

                if (existing == null || link.strength() > existing.role.strength())
                {
                    this.links.put(bone, new Link(chain, link));
                }
            }
        }
    }

    /**
     * Marks what one step of the rig has on the bones, as the rig reports it (see {@link RigOwners}):
     * the bone a setting lives on and its chain along the branches into {@code slot}, what the chain
     * reaches for and bends toward into {@code readSlot}. A switched-off setting marks gray; a pole
     * only shows while its chain is on.
     */
    public void step(RigOwners owners, RigStep step, int slot, int readSlot)
    {
        /* Targets before poles: a bone that is both reads as the target, as it always has. */
        for (RigClaim claim : owners.getClaims())
        {
            if (claim.step() != step)
            {
                continue;
            }

            if (claim.part() == RigClaim.Part.HOME)
            {
                this.chain(claim.chain(), claim.on() ? BoneRole.LINK : BoneRole.LINK.off());
                this.mark(claim.bone(), slot, claim.on() ? BoneRole.CHAIN : BoneRole.CHAIN.off());
            }
            else if (claim.part() == RigClaim.Part.TARGET)
            {
                this.mark(claim.bone(), readSlot, claim.on() ? BoneRole.TARGET : BoneRole.TARGET.off());
            }
        }

        for (RigClaim claim : owners.getClaims())
        {
            if (claim.step() == step && claim.part() == RigClaim.Part.POLE && claim.on())
            {
                this.mark(claim.bone(), readSlot, BoneRole.POLE);
            }
        }
    }

    /** Brightens every bone one step of the rig has something on, without a mark. */
    public void touch(RigOwners owners, RigStep step)
    {
        for (RigClaim claim : owners.getClaims())
        {
            if (claim.step() == step)
            {
                this.touch(claim.bone());
            }
        }
    }

    public boolean isTouched(String bone)
    {
        return this.touched.contains(bone);
    }

    public BoneRole[] getMarks(String bone)
    {
        return this.marks.get(bone);
    }

    /** The chain reaching into this bone from above, if any. */
    public Link getLink(String bone)
    {
        return this.links.get(bone);
    }

    Map<String, Link> getLinks()
    {
        return this.links;
    }

    void clear()
    {
        this.touched.clear();
        this.marks.clear();
        this.links.clear();
    }

    /**
     * The chain a bone belongs to, seen from that bone. The branch into the bone is the
     * chain's only when the parent row the tree shows is in the same chain — with a hidden
     * bone in between the tree skips it, and the chain still reads as one line.
     */
    public record Link(Set<String> chain, BoneRole role)
    {
    }
}
