package gitlet;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

public class Stage implements Serializable {
    /**
     * with gitlet add command, modified file will be added to
     * staging area, to achieve this, use hashmap,
     * which enanle this class to keep track of
     * the modified file name and contents it self
     * with gitlet rm command, removed file will
     * be also added to staging are,to achieve this,
     * use hashset,which only keep the removed file's name
     */

    private HashMap<String, String> added;
    private HashSet<String> removed;

    public Stage(HashMap<String, String> added, HashSet<String> removed) {
        this.added = added;
        this.removed = removed;
    }

    public Stage() {
        this.added = new HashMap<>();
        this.removed = new HashSet<>();
    }

    public HashMap<String, String> getAdded() {
        return added;
    }

    public HashSet<String> getRemoved() {
        return removed;
    }
}
