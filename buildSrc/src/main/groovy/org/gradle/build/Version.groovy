package org.gradle.build

import java.text.SimpleDateFormat
import org.gradle.api.GradleException

class Version {
    String versionNumber
    Date buildTime
    Boolean release = null

    def Version(project) {
        this.versionNumber = project.nextVersion
        File timestampFile = new File(project.buildDir, 'timestamp.txt')
        if (!timestampFile.isFile()) {
            timestampFile.parentFile.mkdirs()
            timestampFile.createNewFile()
        } else {
            boolean uptodate = true
            def modified = timestampFile.lastModified()
            project.project(':core').fileTree('src/main').visit {fte ->
                if (fte.file.isFile() && fte.lastModified > modified) {
                    uptodate = false
                    fte.stopVisiting()
                }
            }
            if (!uptodate) {
                timestampFile.setLastModified(new Date().time)
            }
        }
        buildTime = new Date(timestampFile.lastModified())

        project.gradle.taskGraph.whenReady {graph ->
            if (!graph.hasTask(':releaseVersion')) {
                this.versionNumber += "-" + getTimestamp()
                release = false
            } else {
                release = true
            }
        }
    }

    String toString() {
        versionNumber
    }

    String getTimestamp() {
        new SimpleDateFormat('yyyyMMddHHmmssZ').format(buildTime)
    }

    boolean isRelease() {
        if (release == null) {
            throw new GradleException("Can't determine whether this is a release build before the task graph is populated")
        }
        return release
    }

    String getDistributionUrl() {
        if (release) {
            'https://dav.codehaus.org/dist/gradle'
        } else {
            'https://dav.codehaus.org/snapshots.dist/gradle'
        }
    }
}
