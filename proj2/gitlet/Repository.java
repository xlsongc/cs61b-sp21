package gitlet;

import jdk.jshell.execution.Util;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /* TODO: fill in the rest of this class. */
    public static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    public static final File HEAD = join(GITLET_DIR, "HEAD");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File STAGE = join(GITLET_DIR, "stage");


    public static void init() {
        // 1.check if .gitlet already exists
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdir();
            BRANCHES_DIR.mkdir();
            //HEAD is a file, for now it's not necessary
            OBJECTS_DIR.mkdir();
            Commit initialCommit = new Commit("initial commit",new Date(0), null, new HashMap<>());
            String commitSha1 = sha1(serialize(initialCommit));

            File COMMIT_FILE = Utils.join(OBJECTS_DIR, commitSha1);
            Utils.writeObject(COMMIT_FILE, initialCommit);
            File masterBranch = join(BRANCHES_DIR, "master");
            Utils.writeContents(masterBranch, commitSha1);
            Utils.writeContents(HEAD, "master");
        } else {
            throw new GitletException("A Gitlet version-control system already exists in the current directory.");
        }
    }

    public static void add(String fileName) {
        // 1. check whether the file exists, if it exists, next step, if not, error msg
        // 2. read the hashmap in the current staging area, if the Stage class not exist yet, create one
        // 3. calculate the sha 1 hashkey of filename and blob itself
        // 4. compare the hashkey of blob with the one in current commit, if not same, update it, overwrite current blob if same,clear staging area, do not stage it
        // 5. save Stage instance
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        File fileName_DIR = join(CWD, fileName);
        if (!fileName_DIR.exists()) {
            throw new GitletException("File does not exist.");
        }
        String fileHash = Utils.sha1(Utils.readContents(fileName_DIR));
        String branchName = Utils.readContentsAsString(HEAD);
        File branchFile = join(BRANCHES_DIR, branchName);
        String commitSha = Utils.readContentsAsString(branchFile);
        //System.out.println("commitSha: '" + commitSha + "'");

        File currentCommitBlob = join(OBJECTS_DIR, commitSha);
        //System.out.println("exists: " + currentCommitBlob.exists());
        //System.out.println("path: " + currentCommitBlob.getPath());
        Commit currentCommit = Utils.readObject(currentCommitBlob, Commit.class);
        HashMap<String, String> currentFileMap = currentCommit.getFileMap();
        String currentCommitSha = currentFileMap.get(fileName);
        Stage stage;

        if (fileHash.equals(currentCommitSha)) {
            //currentFileMap.put(fileName, fileHash); //this step is used to update the commit, it needs to be put after the commit command
            if (!STAGE.exists()) {
                stage = new Stage();
            } else {
                stage = Utils.readObject(STAGE, Stage.class);
                HashMap<String, String> currentStageAdded = stage.getAdded();
                currentStageAdded.remove(fileName); // won't throw an error even if current stage doesn't include the filename
            }
        } else {
            HashMap<String, String> currentStageAdded;
            if (!STAGE.exists()) {
                stage = new Stage();
                currentStageAdded = stage.getAdded();
            } else {
                stage = Utils.readObject(STAGE, Stage.class);
                currentStageAdded = stage.getAdded();
            }
            currentStageAdded.put(fileName, fileHash);
            File blobFile = join(OBJECTS_DIR, fileHash);
            Utils.writeContents(blobFile, Utils.readContents(fileName_DIR));
        }
        Utils.writeObject(STAGE, stage);
    }

    public static void commit(String commitMessage) {
        /** 1. get commit message from main.java
         *  2. check the HEAD file to get the current BRANCH name
         *  3. check the branch file and get the commit obj name
         *  4. creat a new commit obj based on the last commit obj, initialize the new commit's fileMap with last parental commit's fileMap
         *  5. check the staging area and added to the current commit obj, clear staging added area
         *  6. check the staging area and if removed is not null, for the current commit obj, take the removed file from the commit's filemap, then clear staging removed area
         *  7. if the new commit obj is complete and, store the commit itself in the blob directory
         */
        // make sure the project is a repo
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        if (!STAGE.exists()) {
            throw new  GitletException("Nothing to commit yet.");
        }

        Stage stage = Utils.readObject(STAGE, Stage.class);
        //String branchName = Utils.readContentsAsString(HEAD);
        //File branchDir = join(BRANCHES_DIR, branchName);
        //String parentCommitSha = Utils.readContentsAsString(branchDir);
        //File currentCommitBlob = join(OBJECTS_DIR, parentCommitSha);
        //Commit currentCommit = Utils.readObject(currentCommitBlob, Commit.class);
        Commit currentCommit = getCurrentCommit();
        HashMap<String, String> currentFileMap = currentCommit.getFileMap();
        HashMap<String, String> newCommitFileMap = new HashMap<>(currentFileMap);
        if (stage.getAdded().isEmpty() && stage.getRemoved().isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        }
        newCommitFileMap.putAll(stage.getAdded());
        for (String fileName : stage.getRemoved()) {
            newCommitFileMap.remove(fileName);
        }
        // step 5 and step 6 clear stage variable
        stage.getAdded().clear();
        stage.getRemoved().clear();
        Utils.writeObject(STAGE, stage);
        // step 7
        String parentCommitSha = getCurrentCommitSha();
        Commit newCommit = new Commit(commitMessage,new Date(), parentCommitSha, newCommitFileMap);
        String newCommitSha = sha1(serialize(newCommit));
        Utils.writeContents(getCurrentBranch(), newCommitSha);
        File commitFile = join(OBJECTS_DIR, newCommitSha);
        Utils.writeObject(commitFile, newCommit);
    }

    public static void rm(String fileName) {
        /**
         * 1. get the command from main to remove the file, check whether the proj folder is a repo, if not throw exception
         * 2. check the condition of the file needs to be removed,
         *      1) if the file is in the staged area and already being tracked by previous commits, add the file to the removed area, remove the file if the file is still in working directory
         *      2) if the file is in the staged and not tracked by commit, remove the file from stage added area
         *      3) if the file is not in the stage area nor being tracked by commit, throw an exception "No reason to remove the file."
         * 3. write back the stage obj
         */
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        if (!STAGE.exists()) {
            throw new  GitletException("No reason to remove the file.");
        }
        File fileName_DIR = join(CWD, fileName);
        // this check will cause an error if a file was deleted by user but commit still contains the track info
        //if (!fileName_DIR.exists()) {
          //  throw new GitletException("File does not exist.");
        //}
        Stage stage = Utils.readObject(STAGE, Stage.class);
        HashMap<String, String> currStageAdded = stage.getAdded();
        HashSet<String> currStageRemoved = stage.getRemoved();

        Commit currCommit = getCurrentCommit();
        String fileSha1 = sha1(serialize(fileName_DIR));
        if (!currCommit.getFileMap().containsKey(fileName)) {
            if (currStageAdded.containsKey(fileName)) {
                stage.getAdded().remove(fileSha1);
            } else {
                throw new GitletException("No reason to remove the file.");
            }
        } else {
            if (currStageAdded.containsKey(fileName)) {
                stage.getAdded().remove(fileName);
                stage.getRemoved().add(fileName);
            } else {
                stage.getRemoved().add(fileName);
            }
            Utils.restrictedDelete(fileName_DIR);
        }
        Utils.writeObject(STAGE, stage);
    }

    private static File getCurrentBranch() {
        String branchName = Utils.readContentsAsString(HEAD);
        return join(BRANCHES_DIR, branchName);
    }

    private static String getCurrentCommitSha() {
        File branchDir = getCurrentBranch();
        return Utils.readContentsAsString(branchDir);
    }

    private static Commit getCurrentCommit() {
        String parentCommitSha = getCurrentCommitSha();
        File currentCommitBlob = join(OBJECTS_DIR, parentCommitSha);
        return Utils.readObject(currentCommitBlob, Commit.class);
    }



}
