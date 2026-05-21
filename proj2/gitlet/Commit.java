package gitlet;

// TODO: any imports you need here

import javax.lang.model.type.NullType;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date; // TODO: You'll likely use this in this class
import java.util.HashMap;


/** Represents a gitlet commit object.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @xiaolong TODO
 */
public class Commit implements Serializable {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private String message;

    /* TODO: fill in the rest of this class. */
    private Date timestamp;
    private String parent;
    private HashMap<String, String> fileMap;

    public Commit(String message, Date timestamp, String parent, HashMap<String, String> fileMap) {
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

    public String getParent() {
        return parent;
    }

    public String getMessage() {
        return message;
    }


}
