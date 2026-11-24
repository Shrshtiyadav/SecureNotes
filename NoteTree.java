import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Works out how notes and their sub notes are arranged in the list
public class NoteTree {

    // One line of the list
    public static class Row {
        public final Note note;
        public final int depth;
        public final boolean hasChildren;
        public final boolean collapsed;

        Row(Note note, int depth, boolean hasChildren, boolean collapsed) {
            this.note = note;
            this.depth = depth;
            this.hasChildren = hasChildren;
            this.collapsed = collapsed;
        }
    }

    public static List<Note> live(List<Note> all) {
        List<Note> result = new ArrayList<>();
        for (Note n : all) {
            if (!n.deleted) {
                result.add(n);
            }
        }
        return result;
    }

    public static List<Note> deleted(List<Note> all) {
        List<Note> result = new ArrayList<>();
        for (Note n : all) {
            if (n.deleted) {
                result.add(n);
            }
        }
        return result;
    }

    private static Map<String, Note> byId(List<Note> notes) {
        Map<String, Note> map = new HashMap<>();
        for (Note n : notes) {
            map.put(n.id, n);
        }
        return map;
    }

    // The main note this one belongs to (null if it is a main note, or its main note is gone)
    public static Note parentOf(Note n, List<Note> live) {
        if (n.parentId == null || n.parentId.isEmpty() || n.parentId.equals(n.id)) {
            return null;
        }
        return byId(live).get(n.parentId);
    }

    public static List<Note> childrenOf(Note parent, List<Note> live) {
        List<Note> kids = new ArrayList<>();
        for (Note n : live) {
            if (n != parent && parent.id.equals(n.parentId)) {
                kids.add(n);
            }
        }
        kids.sort(Comparator.comparingLong((Note n) -> n.created));
        return kids;
    }

    // Every sub note below a note, at any depth
    public static List<Note> descendants(Note parent, List<Note> live) {
        List<Note> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(parent.id);
        collect(parent, live, result, seen);
        return result;
    }

    private static void collect(Note parent, List<Note> live, List<Note> out, Set<String> seen) {
        for (Note kid : childrenOf(parent, live)) {
            if (seen.add(kid.id)) {
                out.add(kid);
                collect(kid, live, out, seen);
            }
        }
    }

    public static int depth(Note n, List<Note> live) {
        int d = 0;
        Note current = n;
        Set<String> seen = new HashSet<>();
        while (seen.add(current.id)) {
            Note p = parentOf(current, live);
            if (p == null) {
                break;
            }
            d++;
            current = p;
        }
        return d;
    }

    // The notes in the order they appear: main notes (pinned first, newest first),
    // each followed by its sub notes. With a search word, a flat list of matches.
    public static List<Row> visible(List<Note> all, Set<String> collapsed, String filter) {
        List<Note> live = live(all);
        List<Row> rows = new ArrayList<>();
        Comparator<Note> mainOrder = (a, b) -> {
            if (a.pinned != b.pinned) {
                return a.pinned ? -1 : 1;
            }
            return Long.compare(b.modified, a.modified);
        };
        String word = filter == null ? "" : filter.trim().toLowerCase();
        if (!word.isEmpty()) {
            List<Note> hits = new ArrayList<>();
            for (Note n : live) {
                if (matches(n, word)) {
                    hits.add(n);
                }
            }
            hits.sort(mainOrder);
            for (Note n : hits) {
                rows.add(new Row(n, 0, false, false));
            }
            return rows;
        }
        Map<String, Note> ids = byId(live);
        List<Note> roots = new ArrayList<>();
        for (Note n : live) {
            if (parentOf(n, live) == null) {
                roots.add(n);
            }
        }
        roots.sort(mainOrder);
        Set<String> seen = new HashSet<>();
        for (Note root : roots) {
            addWithKids(root, 0, live, collapsed, rows, seen);
        }
        // anything left over (for example a loop in the saved data) is shown as a main note
        for (Note n : live) {
            if (!seen.contains(n.id)) {
                rows.add(new Row(n, 0, false, false));
                seen.add(n.id);
            }
        }
        return rows;
    }

    private static void addWithKids(Note n, int depth, List<Note> live, Set<String> collapsed,
                                    List<Row> rows, Set<String> seen) {
        if (!seen.add(n.id)) {
            return;
        }
        List<Note> kids = childrenOf(n, live);
        boolean isCollapsed = collapsed.contains(n.id);
        rows.add(new Row(n, depth, !kids.isEmpty(), isCollapsed));
        if (isCollapsed) {
            for (Note hidden : descendants(n, live)) {
                seen.add(hidden.id);          // hidden, but not lost
            }
        } else {
            for (Note kid : kids) {
                addWithKids(kid, depth + 1, live, collapsed, rows, seen);
            }
        }
    }

    // Does a note contain the word (in its title or its text)?
    public static boolean matches(Note n, String lowerWord) {
        if (n.title.toLowerCase().contains(lowerWord)) {
            return true;
        }
        return RichText.plainText(n.body).toLowerCase().contains(lowerWord);
    }
}