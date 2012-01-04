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
import org.gradle.api.Project

class Version {
    private final Closure versionNumberProvider
    final Date buildTime
    private final Closure isReleaseProvider

    static forProject(Project project) {
        def versionNumber = project.releases.nextVersion
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
        def buildTime = createDateFormat().format(new Date(timestampFile.lastModified()))

        def release = null
        project.gradle.taskGraph.whenReady { graph ->
            if (graph.hasTask(':releaseVersion')) {
                release = true
            } else {
                versionNumber += "-" + buildTime
                release = false
            }
        }

        def isRelease = {
            if (release == null) {
                throw new GradleException("Can't determine whether this is a release build before the task graph is populated")
            }
            release
        }

        new Version({ versionNumber }, buildTime, isRelease)
    }

    Version(Closure versionNumberProvider, String buildTime, Closure isReleaseProvider) {
        this(versionNumberProvider, createDateFormat().parse(buildTime), isReleaseProvider)
    }

    Version(Closure versionNumberProvider, Date buildTime, Closure isReleaseProvider) {
        this.versionNumberProvider = versionNumberProvider
        this.buildTime = buildTime
        this.isReleaseProvider = isReleaseProvider
    }

    static createDateFormat() {
        new SimpleDateFormat('yyyyMMddHHmmssZ')
    }

    String toString() {
        versionNumber
    }

    String getVersionNumber() {
        versionNumberProvider()
    }

    String getTimestamp() {
        createDateFormat().format(buildTime)
    }

    boolean isRelease() {
        isReleaseProvider()
    }

    String getDistributionUrl() {
        if (release) {
            'https://gradle.artifactoryonline.com/gradle/distributions'
        } else {
            'https://gradle.artifactoryonline.com/gradle/distributions-snapshots'
        }
    }

    String getLibsUrl() {
        if (release) {
            'https://gradle.artifactoryonline.com/gradle/libs-releases-local'
        } else {
            'https://gradle.artifactoryonline.com/gradle/libs-snapshots-local'
        }
    }

    def docUrl(docLabel) {
        "http://www.gradle.org/doc/${-> release ? 'current' : toString()}/$docLabel"
    }

    def getJavadocUrl() {
        docUrl("javadoc")
    }

    def getGroovydocUrl() {
        docUrl("groovydoc")
    }

    def getDsldocUrl() {
        docUrl("dsl")
    }
}
