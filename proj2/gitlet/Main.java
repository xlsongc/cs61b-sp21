package gitlet;

import java.io.File;
import java.util.Arrays;

import static gitlet.Repository.*;
import static gitlet.Utils.*;
/**
 * @xiaolong
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            Utils.message("Must have at least one argument");
        }

        switch (args[0]) {
            case "init":
                Repository.init();
                break;
            case "add":
                if (args.length < 2) {
                    throw new GitletException("Please enter the file name");
                }
                Repository.add(args[1]);
                break;
            case "commit":
                if (args.length < 2) {
                    throw new GitletException("Please enter the commit message");
                }
                Repository.commit(args[1]);
                break;
            case "rm":
                if (args.length < 2) {
                    throw new GitletException("Please enter the file name");
                }
                Repository.rm(args[1]);
                break;
            case "log":
                Repository.log();
                break;
            default:
                Utils.message(String.format("Unknown command: %s", args[0]));
                System.exit(0);
        }
        return;
    }
}
