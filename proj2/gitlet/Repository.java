package gitlet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import static gitlet.Utils.*;


public class Repository {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    public static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    public static final File HEAD = join(GITLET_DIR, "HEAD");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    public static final File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    public static final File STAGE = join(GITLET_DIR, "stage");


    public static void init() {
        // 1.check if .gitlet already exists
        if (!GITLET_DIR.exists()) {
            GITLET_DIR.mkdir();
            BRANCHES_DIR.mkdir();
            //HEAD is a file, for now it's not necessary
            OBJECTS_DIR.mkdir();
            COMMITS_DIR.mkdir();
            BLOBS_DIR.mkdir();
            Commit initialCommit = new Commit("initial commit",
                    new Date(0), new ArrayList<>(), new HashMap<>());
            String commitSha1 = sha1(serialize(initialCommit));

            File commitFile = Utils.join(COMMITS_DIR, commitSha1);
            Utils.writeObject(commitFile, initialCommit);
            File masterBranch = join(BRANCHES_DIR, "master");
            Utils.writeContents(masterBranch, commitSha1);
            Utils.writeContents(HEAD, "master");
        } else {
            throw new GitletException("A Gitlet version-control "
                    + "system already exists in the current directory.");
        }
    }

    public static void add(String fileName) {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        File fileNameDir = join(CWD, fileName);
        if (!fileNameDir.exists()) {
            throw new GitletException("File does not exist.");
        }
        String fileHash = Utils.sha1(Utils.readContents(fileNameDir));
        String branchName = Utils.readContentsAsString(HEAD);
        File branchFile = join(BRANCHES_DIR, branchName);
        String commitSha = Utils.readContentsAsString(branchFile);
        File currentCommitBlob = join(COMMITS_DIR, commitSha);
        Commit currentCommit = Utils.readObject(currentCommitBlob, Commit.class);
        HashMap<String, String> currentFileMap = currentCommit.getFileMap();
        String currentCommitSha = currentFileMap.get(fileName);
        Stage stage;

        if (fileHash.equals(currentCommitSha)) {
            if (!STAGE.exists()) {
                stage = new Stage();
            } else {
                stage = Utils.readObject(STAGE, Stage.class);
                HashMap<String, String> currentStageAdded = stage.getAdded();
                HashSet<String> currentStageRemoved = stage.getRemoved();
                currentStageAdded.remove(fileName);
                currentStageRemoved.remove(fileName);
            }
        } else {
            HashMap<String, String> currentStageAdded;
            HashSet<String> currentStageRemoved;
            if (!STAGE.exists()) {
                stage = new Stage();
            } else {
                stage = Utils.readObject(STAGE, Stage.class);
            }
            currentStageAdded = stage.getAdded();
            currentStageRemoved = stage.getRemoved();
            currentStageAdded.put(fileName, fileHash);
            currentStageRemoved.remove(fileName);
            File blobFile = join(BLOBS_DIR, fileHash);
            Utils.writeContents(blobFile, Utils.readContents(fileNameDir));
        }
        Utils.writeObject(STAGE, stage);
    }

    public static void commit(String commitMessage) {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        if (!STAGE.exists()) {
            throw new  GitletException("No changes added to the commit.");
        }
        Stage stage = Utils.readObject(STAGE, Stage.class);
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
        List<String> parentList = new ArrayList<>();
        parentList.add(parentCommitSha);
        Commit newCommit = new Commit(commitMessage, new Date(), parentList, newCommitFileMap);
        String newCommitSha = sha1(serialize(newCommit));
        Utils.writeContents(getCurrentBranch(), newCommitSha);
        File commitFile = join(COMMITS_DIR, newCommitSha);
        Utils.writeObject(commitFile, newCommit);
    }

    public static void rm(String fileName) {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        if (!STAGE.exists()) {
            throw new  GitletException("No reason to remove the file.");
        }
        File fileNameDir = join(CWD, fileName);
        Stage stage = Utils.readObject(STAGE, Stage.class);
        HashMap<String, String> currStageAdded = stage.getAdded();
        HashSet<String> currStageRemoved = stage.getRemoved();

        Commit currCommit = getCurrentCommit();
        if (!currCommit.getFileMap().containsKey(fileName)) {
            if (currStageAdded.containsKey(fileName)) {
                stage.getAdded().remove(fileName);
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
            Utils.restrictedDelete(fileNameDir);
        }
        Utils.writeObject(STAGE, stage);
    }

    public static void log() {
        Commit currCommit = getCurrentCommit();
        SimpleDateFormat sdf = new SimpleDateFormat("E MMM d HH:mm:ss yyyy Z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-08:00"));
        String currCommitSha = getCurrentCommitSha();
        while (true) {
            //String currCommitSha = Utils.sha1(serialize(currCommit));
            Date currCommitTimestamp = currCommit.getTimestamp();
            String currCommitTimestampFormat = sdf.format(currCommitTimestamp);
            String currCommitMessage = currCommit.getMessage();
            String currCommitParent = currCommit.getFirstParent();
            System.out.printf("===%ncommit %s%n%s%n%s%n%n", currCommitSha,
                    "Date: " + currCommitTimestampFormat, currCommitMessage);
            if (currCommit.getFirstParent() == null) {
                break;
            }
            currCommitSha = currCommit.getFirstParent();
            currCommit = getFirstParentCommit(currCommit);
        }
    }

    public static void globalLog() {
        List<String> commitList = Utils.plainFilenamesIn(COMMITS_DIR);
        SimpleDateFormat sdf = new SimpleDateFormat(
                "E MMM d HH:mm:ss yyyy Z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT-08:00"));
        for (String commitFileName : commitList) {
            File commitFIle = join(COMMITS_DIR, commitFileName);
            Commit currCommit = Utils.readObject(commitFIle, Commit.class);
            Date currCommitTimestamp = currCommit.getTimestamp();
            String currCommitTimestampFormat = sdf.format(currCommitTimestamp);
            String currCommitMessage = currCommit.getMessage();
            System.out.printf("===%ncommit %s%n%s%n%s%n%n", commitFileName,
                    "Date: " + currCommitTimestampFormat, currCommitMessage);
        }
    }


    public static void find(String commitMessage) {
        List<String> commitList = Utils.plainFilenamesIn(COMMITS_DIR);
        Integer count = 0;
        for (String commitFileName : commitList) {
            File commitFIle = join(COMMITS_DIR, commitFileName);
            Commit currCommit = Utils.readObject(commitFIle, Commit.class);
            String currCommitMessage = currCommit.getMessage();
            if (commitMessage.equals(currCommitMessage)) {
                System.out.println(commitFileName);
                count += 1;
            }
        }
        if (count == 0) {
            throw new GitletException("Found no commit with that message.");
        }
    }

    public static void status() {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not in an initialized Gitlet directory.");
        }
        String currBranchName = Utils.readContentsAsString(HEAD);
        System.out.println("=== Branches ===");
        System.out.println("*" + currBranchName);
        List<String> branchNameList = Utils.plainFilenamesIn(BRANCHES_DIR);
        Collections.sort(branchNameList);
        for (String branchName : branchNameList) {
            if (!branchName.equals(currBranchName)) {
                System.out.println(branchName);
            }
        }
        System.out.println("\n=== Staged Files ===");
        if (STAGE.exists()) {
            Stage stage = Utils.readObject(STAGE, Stage.class);
            HashMap<String, String> currentStageAdded = stage.getAdded();
            for (String fileName : currentStageAdded.keySet()) {
                System.out.println(fileName);
            }
        }
        System.out.println("\n=== Removed Files ===");
        if (STAGE.exists()) {
            Stage stage = Utils.readObject(STAGE, Stage.class);
            HashSet<String> currStageRemoved = stage.getRemoved();
            for (String fileName : currStageRemoved) {
                System.out.println(fileName);
            }
        }
        System.out.println("\n=== Modifications Not Staged For Commit ===");
        Commit currCommit = getCurrentCommit();
        HashMap<String, String> currCommitFileMap = currCommit.getFileMap();
        for (String fileName : currCommitFileMap.keySet()) {
            File workFile = join(CWD, fileName);
            String currCommitFileSha1 = currCommitFileMap.get(fileName);
            if (STAGE.exists()) {
                Stage stage = Utils.readObject(STAGE, Stage.class);
                HashMap<String, String> currentStageAdded = stage.getAdded();
                HashSet<String> currStageRemoved = stage.getRemoved();
                if (!currentStageAdded.containsKey(fileName)) {
                    if (workFile.exists()) {
                        String workFileSha1 = Utils.sha1(Utils.readContents(workFile));
                        if (!workFileSha1.equals(currCommitFileSha1)) {
                            System.out.println(fileName + " (modified)");
                        }
                    } else {
                        if (!currStageRemoved.contains(fileName)) {
                            System.out.println(fileName + " (deleted)");
                        }
                    }
                } else {
                    String currAddedSha1 = stage.getAdded().get(fileName);
                    if (workFile.exists()) {
                        String workFileSha1 = Utils.sha1(Utils.readContents(workFile));
                        if (!workFileSha1.equals(currAddedSha1)) {
                            System.out.println(fileName + " (modified)");
                        }
                    } else {
                        System.out.println(fileName + " (deleted)");
                    }
                }
            }
        }
        System.out.println("\n=== Untracked Files ===");
        List<String> currWorkFileList = Utils.plainFilenamesIn(CWD);
        if (STAGE.exists()) {
            Stage stage = Utils.readObject(STAGE, Stage.class);
            HashMap<String, String> currentStageAdded = stage.getAdded();
            HashSet<String> currStageRemoved = stage.getRemoved();
            for (String fileName : currWorkFileList) {
                if (!currentStageAdded.containsKey(fileName)) {
                    if (!currCommitFileMap.containsKey(fileName)) {
                        System.out.println(fileName);
                    } else {
                        if (currStageRemoved.contains(fileName)) {
                            System.out.println(fileName);
                        }
                    }
                }
            }
        } else {
            for (String fileName : currWorkFileList) {
                if (!currCommitFileMap.containsKey(fileName)) {
                    System.out.println(fileName);
                }
            }
        }
        System.out.println();
    }

    public static void checkout(String fileName, String commitId, String branchName) {
        if (fileName != null && commitId == null && branchName == null) {
            String currCommitSha1 = getCurrentCommitSha();
            overwriteWorkingFile(fileName, currCommitSha1);
        }
        if (fileName != null && commitId != null && branchName == null) {
            String fullCommitId = getFullCommitId(commitId);
            overwriteWorkingFile(fileName, fullCommitId);
        }
        if (fileName == null && commitId == null && branchName != null) {
            File branchFile = join(BRANCHES_DIR, branchName);
            if (!branchFile.exists()) {
                throw new GitletException("No such branch exists.");
            }
            if (branchFile.equals(getCurrentBranch())) {
                throw new GitletException("No need to checkout the current branch.");
            }
            String checkoutCommitSha1 = Utils.readContentsAsString(branchFile);
            overwriteCurrCommit(checkoutCommitSha1);
            // update the current branch with checkout branch in HEAD
            Utils.writeContents(HEAD, branchName);
        }
    }

    public static void branch(String branchName) {
        // failure mode 1: check whether the branch already exists
        List<String> branchList = Utils.plainFilenamesIn(BRANCHES_DIR);
        if (branchList.contains(branchName)) {
            throw new GitletException("A branch with that name already exists.");
        }
        String currCommitSha1 = getCurrentCommitSha();
        File newBranch = join(BRANCHES_DIR, branchName);
        Utils.writeContents(newBranch, currCommitSha1);
    }


    public static void rmBranch(String branchName) {
        List<String> branchList = Utils.plainFilenamesIn(BRANCHES_DIR);
        if (!branchList.contains(branchName)) {
            throw new GitletException("A branch with that name does not exist.");
        }
        String currBranchName = Utils.readContentsAsString(HEAD);
        if (currBranchName.equals(branchName)) {
            throw new GitletException("Cannot remove the current branch.");
        }
        File rmBranchFile = join(BRANCHES_DIR, branchName);
        rmBranchFile.delete();
    }


    public static void reset(String abrCommitId) {
        String commitId = getFullCommitId(abrCommitId);
        File targetCommitDir = join(COMMITS_DIR, commitId);
        if (!targetCommitDir.exists()) {
            throw new GitletException("No commit with that id exists.");
        }
        overwriteCurrCommit(commitId);
        // update the current branch with reset commmit ID
        String branchName = Utils.readContentsAsString(HEAD);
        File branchFile = join(BRANCHES_DIR, branchName);
        Utils.writeContents(branchFile, commitId);
    }

    public static void merge(String branchName) {
        // step 0: failure cases check
        boolean hasConflict = false;
        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            throw new GitletException("A branch with that name does not exist.");
        }
        String currBranchName = Utils.readContentsAsString(HEAD);
        if (currBranchName.equals(branchName)) {
            throw new GitletException("Cannot merge a branch with itself.");
        }
        // check whether there are staged additions and removals
        Stage stage = Utils.readObject(STAGE, Stage.class);
        HashMap<String, String> currentStageAdded = stage.getAdded();
        HashSet<String> currStageRemoved = stage.getRemoved();
        if (!currentStageAdded.isEmpty() || !currStageRemoved.isEmpty()) {
            throw new GitletException("You have uncommitted changes.");
        }
        // final check: if there is a file in the current commit
        // but not tracked, however the file is tracked in the given branch
        // this file would be overwritten by the given commit
        // we have the relevant code can be used in helper function
        // need to figure out at which point do we need to do the check
        // 01.06.2026 we need to do this check at the beginning
        // but the code is already in overwriteCurrCommit()
        // put the check code here first, we can simplify it later
        String givenBranchCommitSha1 = Utils.readContentsAsString(branchFile);
        String currBranchCommitSha1 = getCurrentCommitSha();
        File givenBranchCommitFile = join(COMMITS_DIR, givenBranchCommitSha1);
        Commit givenBranchCommit = Utils.readObject(givenBranchCommitFile, Commit.class);
        HashMap<String, String> targetCommitFilemap = givenBranchCommit.getFileMap();
        Commit currBranchCommit = getCurrentCommit();
        HashMap<String, String> currCommitFilemap = currBranchCommit.getFileMap();
        // check the file tracked in the given branch but not tracked in the current branch
        for (String targetFileName : targetCommitFilemap.keySet()) {
            File targetFileWorkingDir = join(CWD, targetFileName);
            if (targetFileWorkingDir.exists()) {
                if (!currCommitFilemap.containsKey(targetFileName)) {
                    throw new GitletException("There is an untracked file "
                            + "in the way; delete it, or add and commit it first.");
                }
            }
        }
        /**
         * step1: check whether the current branch or the given
         * branch is the ancestor of the other one
         *  1.1 use the helper getFirstCommonParent() to get the split point
         *  1.2 check that if the split point is the same commit of
         *      the given branch, meaning the current branch is new,
         *      we do nothing, operation ends with message:
         *      Given branch is an ancesotr of the current branch
         *  1.3 check that if the split is the current branch,
         *  meaning the given branch is new, checkout to this branch,
         *  and output message: Current branch fast-forward
         */
        String splitPointCommitSha1 = getFirstCommonParent(currBranchCommit,
                currBranchCommitSha1, givenBranchCommit, givenBranchCommitSha1);
        File splitPointCommitFile = join(COMMITS_DIR, splitPointCommitSha1);
        Commit splitPointCommit = Utils.readObject(splitPointCommitFile, Commit.class);
        if (splitPointCommitSha1.equals(givenBranchCommitSha1)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        } else if (splitPointCommitSha1.equals(currBranchCommitSha1)) {
            checkout(null, null, branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        // Step 2: After all the exception or special case filtering,
        //      we enter a case that both of the two branches have modified files
        //  2.1 loop all the file in the working directory
        //  2.2 compare the file from the split point commit with
        //      current branch commit and given branch commit
        //      there are a few different cases:
        //      1) file unchanged in both two branches
        //      2) file changed in curr not in given, keep curr, stage
        //      3) file changed in given but not in curr, keep given, stage
        //      !!!complex one
        //      4) file changed in both branches, compare them and merge

        // now we have three filemaps: update we need fouth file list
        //                          - targetCommitFilemap
        //                          - currCommitFilemap
        //                          - splitPointCommitFileMap
        //                          - currWorkFileList
        HashMap<String, String> splitPointCommitFileMap = splitPointCommit.getFileMap();
        //List<String> currWorkFileList = Utils.plainFilenamesIn(CWD);
        List<String> currWorkFileList = new ArrayList<>(Utils.plainFilenamesIn(CWD));
        Stage currStage = Utils.readObject(STAGE, Stage.class);
        for (String splitPointFileName : splitPointCommitFileMap.keySet()) {
            // get the file name not in the split point
            if (currWorkFileList.contains(splitPointFileName)) {
                currWorkFileList.remove(splitPointFileName);
            }
            String splitPointFileNameSha1 = splitPointCommitFileMap.get(splitPointFileName);
            //find file tracked historical blob
            File splitPointFile = join(CWD, splitPointFileName);
            if (targetCommitFilemap.containsKey(splitPointFileName)
                    && currCommitFilemap.containsKey(splitPointFileName)) {
                String targetCommitFileSha1 = targetCommitFilemap.get(splitPointFileName);
                String currCommitFileSha1 = currCommitFilemap.get(splitPointFileName);
                // condition 1: file in split point also appears in both branch
                if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    overwriteWorkingFile(splitPointFileName, givenBranchCommitSha1);
                    currStage.getAdded().put(splitPointFileName, targetCommitFileSha1);
                }
                // condition 2
                if (splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && !splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    continue;
                }
                // condition 3.1 both changed but in the same way
                if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && targetCommitFileSha1.equals(currCommitFileSha1)) {
                    continue;
                }
                // condition 8.1 file stay in both branches but modified in different way
                if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && !splitPointFileNameSha1.equals(currCommitFileSha1)
                        && !targetCommitFileSha1.equals(currCommitFileSha1)) {
                    hasConflict = true;
                    File currCommitFile = join(BLOBS_DIR, currCommitFileSha1);
                    File targetCommitFile = join(BLOBS_DIR, targetCommitFileSha1);
                    String currCommitFileContents = Utils.readContentsAsString(currCommitFile);
                    String targetCommitFileContents = Utils.readContentsAsString(targetCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + currCommitFileContents
                            + "=======\n"
                            + targetCommitFileContents
                            + ">>>>>>>\n";
                    Utils.writeContents(splitPointFile, conflictContents);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);

                }
            }
            // condition 3.2 file removed in both branches
            if  (!targetCommitFilemap.containsKey(splitPointFileName)
                    && !currCommitFilemap.containsKey(splitPointFileName)) {
                continue;
            }
            // condition 6
            if  (!targetCommitFilemap.containsKey(splitPointFileName)
                    && currCommitFilemap.containsKey(splitPointFileName)) {
                String currCommitFileSha1 = currCommitFilemap.get(splitPointFileName);
                if (splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    Utils.restrictedDelete(splitPointFile);
                    currStage.getRemoved().add(splitPointFileName);
                }
                // condition 8.2 deleted in given branch
                if (!splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    hasConflict = true;
                    File currCommitFile = join(BLOBS_DIR, currCommitFileSha1);
                    String currCommitFileContents = Utils.readContentsAsString(currCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + currCommitFileContents
                            + "=======\n"
                            + ">>>>>>>\n";
                    Utils.writeContents(splitPointFile, conflictContents);
                    //add(splitPointFileName);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);
                }
            }
            // condition 7: file in the split point
            // also in current branch, but not in given branch
            if  (targetCommitFilemap.containsKey(splitPointFileName)
                    && !currCommitFilemap.containsKey(splitPointFileName)) {
                //String currCommitFileSha1 = currCommitFilemap.get(splitPointFileName);
                String targetCommitFileSha1 = targetCommitFilemap.get(splitPointFileName);

                // if the file unmodified in the current branch, remove it and stage
                if (splitPointFileNameSha1.equals(targetCommitFileSha1)) {
                    continue; //remain to be absent
                }
                // condition 8.3 deleted in current branch
                if (!splitPointFileNameSha1.equals(targetCommitFileSha1)) {
                    hasConflict = true;
                    File targetCommitFile = join(BLOBS_DIR, targetCommitFileSha1);
                    String targetCommitFileContents = Utils.readContentsAsString(targetCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + "=======\n"
                            + targetCommitFileContents
                            + ">>>>>>>\n";
                    Utils.writeContents(splitPointFile, conflictContents);
                    //add(splitPointFileName);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);
                }
            }
        }
        // condition 4,5: file not present in split point,
        // but file present in both/either of the two
        for (String remainFileName : currCommitFilemap.keySet()) {
            File remainFile = join(CWD, remainFileName);
            // condition 4
            if (!splitPointCommitFileMap.containsKey(remainFileName)
                    && !targetCommitFilemap.containsKey(remainFileName)) {
                continue;
            }
        }

        // condition 5
        for (String remainFileName : targetCommitFilemap.keySet()) {
            File remainFile = join(CWD, remainFileName);
            if (!splitPointCommitFileMap.containsKey(remainFileName)
                    && !currCommitFilemap.containsKey(remainFileName)) {
                String remainFileGivenBranchSha1 = targetCommitFilemap.get(remainFileName);
                File remainFileGivenBranchBlob = join(BLOBS_DIR, remainFileGivenBranchSha1);
                checkout(remainFileName, givenBranchCommitSha1, null);
                byte[] writeFileContents = Utils.readContents(remainFileGivenBranchBlob);
                stageMergeAdd(remainFileName, writeFileContents, currStage);
            }

            // condition 8.4
            // the file was absent at the split point and has
            // different contents in the given and current branches.
            if (!splitPointCommitFileMap.containsKey(remainFileName)
                    && targetCommitFilemap.containsKey(remainFileName)
                    && currCommitFilemap.containsKey(remainFileName)) {
                String targetCommitFileSha1 = targetCommitFilemap.get(remainFileName);
                String currCommitFileSha1 = currCommitFilemap.get(remainFileName);
                if (!targetCommitFileSha1.equals(currCommitFileSha1)) {
                    hasConflict = true;
                    File currCommitFile = join(BLOBS_DIR, currCommitFileSha1);
                    File targetCommitFile = join(BLOBS_DIR, targetCommitFileSha1);
                    String currCommitFileContents = Utils.readContentsAsString(currCommitFile);
                    String targetCommitFileContents = Utils.readContentsAsString(targetCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + currCommitFileContents
                            + "=======\n"
                            + targetCommitFileContents
                            + ">>>>>>>\n";
                    Utils.writeContents(remainFile, conflictContents);
                    //add(remainFileName);
                    stageMergeAdd(remainFileName, conflictContents.getBytes(), currStage);
                }
            }
        }
        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
        }
        Utils.writeObject(STAGE, currStage);
        mergeCommit(currBranchName, branchName, currBranchCommit,
                    currBranchCommitSha1, givenBranchCommitSha1);
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
        String currCommitSha = getCurrentCommitSha();
        File currentCommitBlob = join(COMMITS_DIR, currCommitSha);
        return Utils.readObject(currentCommitBlob, Commit.class);
    }



    private static Commit getFirstParentCommit(Commit currCommit) {
        String parentCommitSha = currCommit.getFirstParent();
        //System.out.printf("parentCommitSha %s",parentCommitSha);
        File parentCommitBlob = join(COMMITS_DIR, parentCommitSha);
        return Utils.readObject(parentCommitBlob, Commit.class);
    }

    private static void overwriteWorkingFile(String fileName, String commitId) {
        File targetCommitDir = join(COMMITS_DIR, commitId);
        if (!targetCommitDir.exists()) {
            throw new GitletException("No commit with that id exists.");
        }
        Commit targetCommit = Utils.readObject(targetCommitDir, Commit.class);
        HashMap<String, String> targetCommitFilemap = targetCommit.getFileMap();
        if (!targetCommitFilemap.containsKey(fileName)) {
            throw new GitletException("File does not exist in that commit.");
        }
        String fileBlobName = targetCommitFilemap.get(fileName);
        File fileBlobDir = join(BLOBS_DIR, fileBlobName);
        File fileWorkingDir = join(CWD, fileName);
        // overwrite file in working dir
        Utils.writeContents(fileWorkingDir, Utils.readContents(fileBlobDir));
    }

    private static void overwriteCurrCommit(String givenCommitId) {
        File targetCommitDir = join(COMMITS_DIR, givenCommitId);
        Commit targetCommit = Utils.readObject(targetCommitDir, Commit.class);
        HashMap<String, String> targetCommitFilemap = targetCommit.getFileMap();

        Commit currCommit = getCurrentCommit();
        HashMap<String, String> currCommitFilemap = currCommit.getFileMap();

        for (String targetFileName : targetCommitFilemap.keySet()) {
            File targetFileWorkingDir = join(CWD, targetFileName);
            if (targetFileWorkingDir.exists()) {
                if (!currCommitFilemap.containsKey(targetFileName)) {
                    throw new GitletException("There is an untracked file in the way; "
                            + "delete it, or add and commit it first.");
                }
            }
            overwriteWorkingFile(targetFileName, givenCommitId);
            currCommitFilemap.remove(targetFileName);
        }
        // after overwrite all the files in working dir that come from reset commit
        // remove the file still in the current branch's commit filemap
        for (String remainFileName : currCommitFilemap.keySet()) {
            File remainFile = join(CWD, remainFileName);
            Utils.restrictedDelete(remainFile);
        }
        if (STAGE.exists()) {
            Stage stage = Utils.readObject(STAGE, Stage.class);
            stage.getAdded().clear();
            stage.getRemoved().clear();
            // write back the stage file
            Utils.writeObject(STAGE, stage);
        }
    }

    private static String getFullCommitId(String abrCommitId) {
        List<String> matchCommitId = new ArrayList<>();
        List<String> fullCommitList = Utils.plainFilenamesIn(COMMITS_DIR);
        for (String fullCommitId : fullCommitList) {
            if (fullCommitId.startsWith(abrCommitId)) {
                matchCommitId.add(fullCommitId);
            }
        }
        if (matchCommitId.size() <= 0) {
            throw new GitletException("No commit with that id exists.");
        }
        if  (matchCommitId.size() >= 2) {
            throw new GitletException("Multi commits with similar commit prefix exist.");
        }
        return matchCommitId.get(0);
    }


    private static String getFirstCommonParent(Commit currCommit,
                                               String currCommitSha1,
                                               Commit givenCommit,
                                               String givenCommitSha1) {
        // first step: using bfs or dfs to do the graph traversal
        // then get a full list of given commit's parent list
        Queue<Commit> givenQueue = new LinkedList<>();
        givenQueue.add(givenCommit);
        HashSet<String> givenCommitParentSet = new HashSet<>();
        //String givenCommitSha1 = Utils.sha1(serialize(givenCommit));
        givenCommitParentSet.add(givenCommitSha1);
        while (!givenQueue.isEmpty()) {
            Commit currGivenCommit = givenQueue.poll();
            List<String> givenCommitParents = currGivenCommit.getParents();
            if (givenCommitParents.size() == 1) {
                String parent1 = givenCommitParents.get(0);
                givenCommitParentSet.add(parent1);
                File parent1CommitFile = join(COMMITS_DIR, parent1);
                Commit parent1Commit = readObject(parent1CommitFile, Commit.class);
                givenQueue.add(parent1Commit);
            } else if (givenCommitParents.size() == 2) {
                String parent1 = givenCommitParents.get(0);
                givenCommitParentSet.add(parent1);
                String parent2 = givenCommitParents.get(1);
                givenCommitParentSet.add(parent2);
                File parent1CommitFile = join(COMMITS_DIR, parent1);
                File parent2CommitFile = join(COMMITS_DIR, parent2);
                Commit parent1Commit = readObject(parent1CommitFile, Commit.class);
                Commit parent2Commit = readObject(parent2CommitFile, Commit.class);
                givenQueue.add(parent1Commit);
                givenQueue.add(parent2Commit);
            }
        }
        // second step: use bfs to do the graph traversal to find
        // the first parent commit that shows in given commit's parent list
        Queue<Commit> currQueue = new LinkedList<>();
        currQueue.add(currCommit);
        if (givenCommitParentSet.contains(currCommitSha1)) {
            return currCommitSha1;
        }
        while (!currQueue.isEmpty()) {
            Commit currWorkCommit = currQueue.poll();
            List<String> currCommitParents = currWorkCommit.getParents();
            if (currCommitParents.size() == 1) {
                String currParent1 = currCommitParents.get(0);
                File currParent1CommitFile = join(COMMITS_DIR, currParent1);
                Commit parent1Commit = Utils.readObject(currParent1CommitFile, Commit.class);
                if (givenCommitParentSet.contains(currParent1)) {
                    return currParent1;
                }
                currQueue.add(parent1Commit);
            } else if (currCommitParents.size() == 2) {
                String currParent1 = currCommitParents.get(0);
                File currParent1CommitFile = Utils.join(COMMITS_DIR, currParent1);
                Commit parent1Commit = Utils.readObject(currParent1CommitFile, Commit.class);
                if (givenCommitParentSet.contains(currParent1)) {
                    return currParent1;
                }
                currQueue.add(parent1Commit);
                // second parent
                String currParent2 = currCommitParents.get(1);
                File currParent2CommitFile = Utils.join(COMMITS_DIR, currParent2);
                Commit parent2Commit = readObject(currParent2CommitFile, Commit.class);
                if (givenCommitParentSet.contains(currParent2)) {
                    return currParent2;
                }
                currQueue.add(parent2Commit);
            }
        }
        throw new GitletException("No common ancestor found!");
    }


    private static void mergeCommit(String currBranchName,
                                    String givenBranchName,
                                    Commit currCommit,
                                    String currCommitSha1,
                                    String givenCommitSha1) {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("Not repo yet, use init to initialize the repo.");
        }
        if (!STAGE.exists()) {
            throw new  GitletException("Nothing to commit yet.");
        }

        Stage stage = Utils.readObject(STAGE, Stage.class);
        HashMap<String, String> currentFileMap = currCommit.getFileMap();
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
        List<String> parentList = new ArrayList<>();
        parentList.add(currCommitSha1);
        parentList.add(givenCommitSha1);
        String commitMessage = String.format("Merged %s into %s.", givenBranchName, currBranchName);
        Commit newCommit = new Commit(commitMessage, new Date(), parentList, newCommitFileMap);
        String newCommitSha = sha1(serialize(newCommit));
        Utils.writeContents(getCurrentBranch(), newCommitSha);
        File commitFile = join(COMMITS_DIR, newCommitSha);
        Utils.writeObject(commitFile, newCommit);
    }

    private static void stageMergeAdd(String fileName, byte[] fileContents, Stage currStage) {
        String fileHash = Utils.sha1(fileContents);
        HashMap<String, String> currentStageAdded = currStage.getAdded();
        currentStageAdded.put(fileName, fileHash);
        File blobFile = join(BLOBS_DIR, fileHash);
        Utils.writeContents(blobFile, fileContents);
        //Utils.writeObject(STAGE, currStage);
    }

}
