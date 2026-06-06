package gitlet;

// TODO: any imports you need here

import java.io.Serializable;

import java.util.Date; // TODO: You'll likely use this in this class
import java.util.HashMap;
import java.util.List;


/** Represents a gitlet commit object.
 *
 *  @xiaolong
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private String message;

    private Date timestamp;
    private List<String> parent;
    private HashMap<String, String> fileMap;

    public Commit(String message, Date timestamp,
                  List<String> parent, HashMap<String, String> fileMap) {
        this.message = message;
        this.parent = parent;
        this.timestamp = timestamp;
        this.fileMap = fileMap;
    }

    public HashMap<String, String> getFileMap() {
        return fileMap;
    }
    public Date getTimestamp() {
        return timestamp;
    }

    public String getFirstParent() {
        if (parent.isEmpty()) {
            return null;
        }
        return parent.get(0);
    }

    public List<String> getParents() {
        return parent;
    }

    public String getMessage() {
        return message;
    }


}
