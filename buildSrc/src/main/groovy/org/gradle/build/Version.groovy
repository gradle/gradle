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

    enum Type {
        ADHOC({ !it.isReleaseBuild() }, { it }), 
        NIGHTLY({ it.isNightlyBuild() }, { "nightly" }), 
        RC({ it.isRcBuild() }, { "release-candidate" }), 
        FINAL({ it.isFinalReleaseBuild() }, { it })
        
        final Closure detector
        final Closure labelProvider
        
        Type(Closure detector, Closure labelProvider) {
            this.detector = detector
            this.labelProvider = labelProvider
        }
    }

    private final Closure versionNumberProvider
    final Date buildTime
    private final Closure typeProvider

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

        def type = null
        project.gradle.taskGraph.whenReady { graph ->
            type = Type.values().find { it.detector(project) }
            if (type != Type.FINAL) {
                versionNumber += "-" + buildTime
            }
        }

        def typeProvider = {
            if (type == null) {
                throw new GradleException("Can't determine whether the type of version for this build before the task graph is populated")
            }
            type
        }

        new Version({ versionNumber }, buildTime, typeProvider)
    }

    Version(Closure versionNumberProvider, String buildTime, Closure typeProvider) {
        this(versionNumberProvider, createDateFormat().parse(buildTime), typeProvider)
    }

    Version(Closure versionNumberProvider, Date buildTime, Closure typeProvider) {
        this.versionNumberProvider = versionNumberProvider
        this.buildTime = buildTime
        this.typeProvider = typeProvider
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
        type == Type.FINAL
    }

    Type getType() {
        typeProvider()
    }

    String getLabel() {
        type.labelProvider(versionNumber)
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
        "http://www.gradle.org/doc/${-> release ? 'current' : label}/$docLabel"
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
