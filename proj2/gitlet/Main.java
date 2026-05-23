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
            case "global-log":
                Repository.globalLog();
                break;
            case "find":
                if (args.length < 2) {
                    throw new GitletException("Please enter the commit message");
                }
                Repository.find(args[1]);
                break;
            case "status":
                Repository.status();
                break;
            case "checkout":
                if (args.length < 2) {
                    throw new GitletException("Please enter the checkout information");
                }
                if (args.length == 2) {
                    Repository.checkout(null, null, args[1]);
                }
                if (args.length == 3 && args[1].equals("--")) {
                    Repository.checkout(args[2], null, null);
                }
                if (args.length == 4 && args[2].equals("--")) {
                    Repository.checkout(args[3], args[1], null);
                }
                break;

            default:
                Utils.message(String.format("Unknown command: %s", args[0]));
                System.exit(0);
        }
        return;
    }
}
