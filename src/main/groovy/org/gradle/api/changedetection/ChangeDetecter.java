/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
