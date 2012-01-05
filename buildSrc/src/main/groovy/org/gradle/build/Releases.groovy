/*
 * Copyright 2011 the original author or authors.
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

import java.util.regex.Pattern
import java.text.SimpleDateFormat
import org.gradle.api.Project

class Releases {
    final File releasesFile
    final Project project

    Releases(File releasesFile, Project project) {
        this.releasesFile = releasesFile
        this.project = project
    }

    String getNextVersion() {
        def releases = load()
        def next = releases.next
        assert next && next[0].'@version'
        return next[0].'@version'
    }

    void generateTo(File resourceFile) {
        modifyTo(resourceFile) {
            next.each { remove(it) }
            current[0].'@version' = this.project.version.versionNumber
            current[0].'@build-time' = this.formattedBuildTime
            current[0].'@type' = this.project.version.release ? 'release' : 'snapshot'
        }
    }

    String calculateNextVersion(String version) {
        def matcher = Pattern.compile("(\\d+(\\.\\d+)*?\\.)(\\d+)((-\\w+-)(\\d)[a-z]?)?").matcher(version)
        if (!matcher.matches()) {
            throw new RuntimeException("Cannot determine the next version after '$version'")
        }
        if (!matcher.group(4)) {
            def minor = matcher.group(3) as Integer
            return "${matcher.group(1)}${minor+1}-milestone-1"
        }
        def minor = matcher.group(6) as Integer
        return "${matcher.group(1)}${matcher.group(3)}${matcher.group(5)}${minor+1}"
    }

    void incrementNextVersion() {
        modifyTo(releasesFile) {
            def nextRelease = next[0]
            assert nextRelease && nextRelease.'@version'
            def thisRelease = nextRelease.'@version'
            nextRelease.@version = this.calculateNextVersion(thisRelease)
            def currentRelease = current[0]
            assert currentRelease
            currentRelease + {
                release(version: thisRelease, "build-time": this.formattedBuildTime)
            }
        }
    }

    private String getFormattedBuildTime() {
        return new SimpleDateFormat("yyyyMMddHHmmssZ").format(project.version.buildTime)
    }

    private load() {
        assert releasesFile.exists()
        new XmlParser().parse(releasesFile)
    }

    public modifyTo(File destination, Closure modifications) {
        def releases = load()
        project.configure(releases, modifications)
        destination.parentFile.mkdirs()
        destination.createNewFile()
        destination.withPrintWriter { writer -> new XmlNodePrinter(writer, "  ").print(releases) }
    }
}
