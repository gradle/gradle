/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build

import org.gradle.api.tasks.Sync

class Install extends Sync {

    String installDirPropertyName
    File installDir

    def Install() {
        addPropertyCheck()
        doLast {
            ant.chmod(file: "$installDir/bin/gradle", perm: 'ugo+x')
        }
    }

    private void addPropertyCheck() {
        project.gradle.taskGraph.whenReady {graph ->
            if (graph.hasTask(path)) {
                // Do this early to ensure that the properties we need have been set, and fail early
                if (!project.hasProperty(installDirPropertyName)) {
                    throw new RuntimeException("You can't install without setting the $installDirPropertyName property.")
                }
                installDir = project.file(this.project."$installDirPropertyName")
                if (installDir.file) {
                    throw new RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
                }
                if (installDir.directory) {
                    if (!(installDir.list() as List).empty) {
                        File libDir = new File(installDir, "lib")
                        if (!libDir.directory || !libDir.list().findAll { it.matches('gradle.*\\.jar')}) {
                            throw new RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
                        }
                    }
                }
                into installDir
            }
        }
    }
}
