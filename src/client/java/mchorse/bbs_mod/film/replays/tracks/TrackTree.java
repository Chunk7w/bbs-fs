package mchorse.bbs_mod.film.replays.tracks;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hangs a timeline's tracks under their owners — the recording, the form and each of its parts —
 * and, inside a form's owner, under its headings (Pose, Rig, Transform, Look; see {@link FormTrackGroup}).
 * Every part is always there: what isn't being worked on is folded, never filtered away.
 */
public class TrackTree
{
    /** The recording's own heading: the replay's channels, an addon's among them. */
    public static final String RECORD = "owner/record";

    /** Headings each timeline's folds have already seen, so only a new one gets its first fold. Folds are kept per replay. */
    private final Map<FoldState<String>, Set<String>> knownGroups = new WeakHashMap<>();

    public static String ownerId(String path)
    {
        return "owner/form/" + path;
    }

    /**
     * Gives every row its heading and reorders the rows so each heading's rows follow it: the
     * recording first, then the parts in the form's own order. A folding tree stays whole: its rows
     * take the heading of the row they fold under.
     *
     * @param parts  the form and its parts, root first — see {@link UIForms#entries}
     * @param record whether the rows of no form, a replay's own channels, go under the recording
     */
    public void groupSheets(List<UIKeyframeSheet> sheets, List<UIForms.FormEntry> parts, FoldState<String> folds, boolean record)
    {
        Map<String, String> names = UIForms.names(parts);
        Map<String, Integer> partOrder = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> owners = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> groups = new HashMap<>();
        Map<String, UIKeyframeSheet.Section> rehung = new HashMap<>();
        Map<UIKeyframeSheet, Integer> rank = new HashMap<>();
        int slots = FormTrackGroup.values().length + 1;
        UIKeyframeSheet.Section recording = record ? UIKeyframeSheet.Section.owner(RECORD, TrackCategory.REPLAY.label, Icons.PLAYER) : null;

        for (UIForms.FormEntry entry : parts)
        {
            partOrder.putIfAbsent(entry.getPath(), partOrder.size());
        }

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.parent != null && sheet.parent.section != null && (rank.containsKey(sheet.parent) || sheet.parent.descriptor == null))
            {
                sheet.section = sheet.parent.section;

                if (rank.containsKey(sheet.parent))
                {
                    rank.put(sheet, rank.get(sheet.parent));
                }

                continue;
            }

            if (sheet.descriptor == null)
            {
                /* Keeps its place in front, in the order it was handed over in. */
                if (recording != null)
                {
                    sheet.section = rehang(sheet.section, recording, rehung);
                }

                continue;
            }

            String path = sheet.descriptor.id().formPath();
            FormTrackGroup group = FormTrackGroup.of(sheet.descriptor.id());
            UIKeyframeSheet.Section owner = owners.computeIfAbsent(path, key -> UIKeyframeSheet.Section.owner(ownerId(key),
                IKey.constant(names.getOrDefault(key, key)), sheet.descriptor.owner().getIcon()));

            if (sheet.section == null)
            {
                sheet.section = group == null ? owner : groups.computeIfAbsent(path + "/" + group.id, key -> group.section(path, owner));
            }
            else
            {
                sheet.section = rehang(sheet.section, owner, rehung);
            }

            /* An addon's track has no heading of ours; it closes its owner's list. */
            int partIndex = partOrder.getOrDefault(path, partOrder.size());

            rank.put(sheet, partIndex * slots + (group == null ? slots - 1 : group.ordinal()));
        }

        Set<String> known = this.knownGroups.computeIfAbsent(folds, k -> new HashSet<>());

        for (UIKeyframeSheet sheet : sheets)
        {
            for (UIKeyframeSheet.Section section = sheet.section; section != null; section = section.parent())
            {
                if (known.add(section.id()) && opensFirst(section))
                {
                    folds.set(section.id(), true);
                }
            }
        }

        /* Stable, so a heading keeps its rows' order and a tree its shape. Rows of no form stay in front. */
        sheets.sort(Comparator.comparingInt(sheet -> rank.getOrDefault(sheet, -1)));
    }

    /** A heading built by someone else, hung under the owner it belongs to; owners and what already hangs there stay. */
    private static UIKeyframeSheet.Section rehang(UIKeyframeSheet.Section section, UIKeyframeSheet.Section owner, Map<String, UIKeyframeSheet.Section> rehung)
    {
        if (section == null)
        {
            return owner;
        }

        if (section.owner() || section.isIn(owner))
        {
            return section;
        }

        String key = owner.id() + "|" + section.id();
        UIKeyframeSheet.Section hung = rehung.get(key);

        if (hung == null)
        {
            hung = section.under(rehang(section.parent(), owner, rehung));
            rehung.put(key, hung);
        }

        return hung;
    }

    /**
     * Whether a heading starts open the first time a timeline shows it. A new heading must not hide
     * tracks that were already in reach: the recording and the form were always shown, and so were
     * a form's headings. A part was shown only once picked, so its owner starts folded; the
     * recording's own headings keep the folds they always started with.
     */
    private static boolean opensFirst(UIKeyframeSheet.Section section)
    {
        if (section.owner())
        {
            return section.id().equals(RECORD) || section.id().equals(ownerId(""));
        }

        for (UIKeyframeSheet.Section parent = section.parent(); parent != null; parent = parent.parent())
        {
            if (parent.id().equals(RECORD))
            {
                return false;
            }
        }

        return true;
    }
}
