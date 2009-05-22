package org.gradle.api.changedetection;

import org.gradle.api.changedetection.state.DirectoryStateChangeDetecter;
import org.gradle.api.changedetection.state.DefaultDirectoryStateChangeDetecterBuilder;

import java.io.File;

/**
 * Detects file changes in a directory and informs a ChangeProcessor about the changes.
 *
 * @author Tom Eyckmans
 */
public class ChangeDetecter {

    public static void main(String[] args) {
        // store state in a .gradle/states directory in this directory
        File projectRootDir = new File(".");
        // directory to process
        File mainGroovyDir = new File(new File("."), "src/main/groovy");

        DirectoryStateChangeDetecter cDetecter = new DefaultDirectoryStateChangeDetecterBuilder()
                .rootProjectDirectory(projectRootDir)
                .directoryToProcess(mainGroovyDir)
                .dotGradleStatesDirectory(new File(projectRootDir, ".gradle/states"))
                .getDirectoryStateChangeDetecter();

        cDetecter.detectChanges(new ChangeProcessor() {
            public void createdFile(File changedFile) {
                System.out.println(changedFile + " [created]");
            }
            public void changedFile(File changedFile) {
                System.out.println(changedFile + " [changed]");
            }
            public void deletedFile(File changedFile) {
                System.out.println(changedFile + " [deleted]");
            }
        });

        

    }
}
