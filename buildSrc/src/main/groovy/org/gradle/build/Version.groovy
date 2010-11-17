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

import java.text.SimpleDateFormat
import org.gradle.api.GradleException

class Version {
    String versionNumber
    Date buildTime
    Boolean release = null

    def Version(project) {
        this.versionNumber = project.nextVersion
        File timestampFile = new File(project.buildDir, 'timestamp.txt')
        if (timestampFile.isFile()) {
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
        } else {
            timestampFile.parentFile.mkdirs()
            timestampFile.createNewFile()
        }
        buildTime = new Date(timestampFile.lastModified())

        project.gradle.taskGraph.whenReady {graph ->
            if (graph.hasTask(':releaseVersion')) {
                release = true
            } else {
                this.versionNumber += "-" + getTimestamp()
                release = false
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
