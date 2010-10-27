package org.gradle.build

import org.gradle.api.tasks.Sync

class Install extends Sync {

    String installDirProperyName
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
                if (!project.hasProperty(installDirProperyName)) {
                    throw new RuntimeException("You can't install without setting the $installDirProperyName property.")
                }
                installDir = project.file(this.project."$installDirProperyName")
                if (installDir.file) {
                    throw new RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
                }
                if (installDir.directory) {
                    File libDir = new File(installDir, "lib")
                    if (!libDir.directory || !libDir.list().findAll { it.matches('gradle.*\\.jar')}) {
                        throw new RuntimeException("Install directory $installDir does not look like a Gradle installation. Cannot delete it to install.")
                    }
                }
                into installDir
            }
        }
    }
}
