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
        Stage stage = STAGE.exists() ? Utils.readObject(STAGE, Stage.class)
                : new Stage();
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
        HashMap<String, String> currentStageAdded = stage.getAdded();
        for (String fileName : currentStageAdded.keySet()) {
            System.out.println(fileName);
        }
        System.out.println("\n=== Removed Files ===");
        HashSet<String> currStageRemoved = stage.getRemoved();
        for (String fileName : currStageRemoved) {
            System.out.println(fileName);
        }
        System.out.println("\n=== Modifications Not Staged For Commit ===");
        Commit currCommit = getCurrentCommit();
        HashMap<String, String> currCommitFileMap = currCommit.getFileMap();
        for (String fileName : currCommitFileMap.keySet()) {
            File workFile = join(CWD, fileName);
            String currCommitFileSha1 = currCommitFileMap.get(fileName);
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
        System.out.println("\n=== Untracked Files ===");
        List<String> currWorkFileList = Utils.plainFilenamesIn(CWD);
        for (String fileName : currWorkFileList) {
            if (!currentStageAdded.containsKey(fileName)) {
                if (!currCommitFileMap.containsKey(fileName)
                        || currStageRemoved.contains(fileName)) {
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
        boolean hasConflict = false;
        File branchFile = join(BRANCHES_DIR, branchName);
        validateMerge(branchName);
        String currBranchName = Utils.readContentsAsString(HEAD);
        Stage stage = Utils.readObject(STAGE, Stage.class);
        String givenBranchCommitSha1 = Utils.readContentsAsString(branchFile);
        String currBranchCommitSha1 = getCurrentCommitSha();
        File givenBranchCommitFile = join(COMMITS_DIR, givenBranchCommitSha1);
        Commit givenBranchCommit = Utils.readObject(givenBranchCommitFile, Commit.class);
        Commit currBranchCommit = getCurrentCommit();
        HashMap<String, String> targetCommitFilemap = givenBranchCommit.getFileMap();
        HashMap<String, String> currCommitFilemap = currBranchCommit.getFileMap();
        checkUntrackedFile(targetCommitFilemap, currCommitFilemap);
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
        HashMap<String, String> splitPointCommitFileMap = splitPointCommit.getFileMap();
        Stage currStage = Utils.readObject(STAGE, Stage.class);
        hasConflict = mergeSplitPointFiles(splitPointCommitFileMap, currCommitFilemap,
                                            targetCommitFilemap,
                                            givenBranchCommitSha1, currStage);
        // condition 4,5: file not present in split point, file present in two
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

    private static void checkUntrackedFile(HashMap<String, String> targetCommitFilemap,
                                           HashMap<String, String> currCommitFilemap) {
        for (String targetFileName : targetCommitFilemap.keySet()) {
            File targetFileWorkingDir = join(CWD, targetFileName);
            if (targetFileWorkingDir.exists()) {
                if (!currCommitFilemap.containsKey(targetFileName)) {
                    throw new GitletException("There is an untracked file "
                            + "in the way; delete it, or add and commit it first.");
                }
            }
        }
    }

    private static String conflictContents(String splitPointFileNameSha1,
                                              String currCommitFileSha1,
                                              String targetCommitFileSha1) {
        File currCommitFile = join(BLOBS_DIR, currCommitFileSha1);
        File targetCommitFile = join(BLOBS_DIR, targetCommitFileSha1);
        String currCommitFileContents = Utils.readContentsAsString(currCommitFile);
        String targetCommitFileContents = Utils.readContentsAsString(targetCommitFile);
        String conflictContents = "<<<<<<< HEAD\n"
                    + currCommitFileContents
                    + "=======\n"
                    + targetCommitFileContents
                    + ">>>>>>>\n";
        return conflictContents;
    }

    private static void validateMerge(String branchName) {
        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            throw new GitletException("A branch with that name does not exist.");
        }
        String currBranchName = Utils.readContentsAsString(HEAD);
        if (currBranchName.equals(branchName)) {
            throw new GitletException("Cannot merge a branch with itself.");
        }
        Stage stage = Utils.readObject(STAGE, Stage.class);
        HashMap<String, String> currentStageAdded = stage.getAdded();
        HashSet<String> currStageRemoved = stage.getRemoved();
        if (!currentStageAdded.isEmpty() || !currStageRemoved.isEmpty()) {
            throw new GitletException("You have uncommitted changes.");
        }
    }

    private static boolean mergeSplitPointFiles(
            HashMap<String, String> splitPointCommitFileMap,
            HashMap<String, String> currCommitFilemap,
            HashMap<String, String> targetCommitFilemap,
            String givenBranchCommitSha1,
            Stage currStage
    ) {
        boolean hasConflict = false;
        for (String splitPointFileName : splitPointCommitFileMap.keySet()) {
            String splitPointFileNameSha1 = splitPointCommitFileMap.get(splitPointFileName);
            File splitPointFile = join(CWD, splitPointFileName);
            if (targetCommitFilemap.containsKey(splitPointFileName)
                    && currCommitFilemap.containsKey(splitPointFileName)) {
                String targetCommitFileSha1 = targetCommitFilemap.get(splitPointFileName);
                String currCommitFileSha1 = currCommitFilemap.get(splitPointFileName);
                if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    overwriteWorkingFile(splitPointFileName, givenBranchCommitSha1);
                    currStage.getAdded().put(splitPointFileName, targetCommitFileSha1);
                } else if (splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && !splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    continue;
                } else if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && targetCommitFileSha1.equals(currCommitFileSha1)) {
                    continue;
                } else if (!splitPointFileNameSha1.equals(targetCommitFileSha1)
                        && !splitPointFileNameSha1.equals(currCommitFileSha1)
                        && !targetCommitFileSha1.equals(currCommitFileSha1)) {
                    hasConflict = true;
                    String conflictContents = conflictContents(splitPointFileNameSha1,
                            currCommitFileSha1, targetCommitFileSha1);
                    Utils.writeContents(splitPointFile, conflictContents);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);
                }
            }
            if  (!targetCommitFilemap.containsKey(splitPointFileName)
                    && !currCommitFilemap.containsKey(splitPointFileName)) {
                continue;
            } else if  (!targetCommitFilemap.containsKey(splitPointFileName)
                    && currCommitFilemap.containsKey(splitPointFileName)) {
                String currCommitFileSha1 = currCommitFilemap.get(splitPointFileName);
                if (splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    Utils.restrictedDelete(splitPointFile);
                    currStage.getRemoved().add(splitPointFileName);
                } else if (!splitPointFileNameSha1.equals(currCommitFileSha1)) {
                    hasConflict = true;
                    File currCommitFile = join(BLOBS_DIR, currCommitFileSha1);
                    String currCommitFileContents = Utils.readContentsAsString(currCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + currCommitFileContents
                            + "=======\n"
                            + ">>>>>>>\n";
                    Utils.writeContents(splitPointFile, conflictContents);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);
                }
            }
            if  (targetCommitFilemap.containsKey(splitPointFileName)
                    && !currCommitFilemap.containsKey(splitPointFileName)) {
                String targetCommitFileSha1 = targetCommitFilemap.get(splitPointFileName);
                if (splitPointFileNameSha1.equals(targetCommitFileSha1)) {
                    continue; //remain to be absent
                } else if (!splitPointFileNameSha1.equals(targetCommitFileSha1)) {
                    hasConflict = true;
                    File targetCommitFile = join(BLOBS_DIR, targetCommitFileSha1);
                    String targetCommitFileContents = Utils.readContentsAsString(targetCommitFile);
                    String conflictContents = "<<<<<<< HEAD\n"
                            + "=======\n"
                            + targetCommitFileContents
                            + ">>>>>>>\n";
                    Utils.writeContents(splitPointFile, conflictContents);
                    stageMergeAdd(splitPointFileName, conflictContents.getBytes(), currStage);
                }
            }
        }
        return hasConflict;
    }
}
