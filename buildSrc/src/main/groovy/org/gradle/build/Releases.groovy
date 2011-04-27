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
        assert releasesFile.exists()
        def releases = new XmlParser().parse(releasesFile)
        def next = releases.next
        assert next && next[0].'@version'
        return next[0].'@version'
    }

    void generateTo(File resourceFile) {
        assert releasesFile.exists()
        def releases = new XmlParser().parse(releasesFile)
        releases.next.each { releases.remove(it) }
        releases.current[0].'@version' = project.version.versionNumber
        releases.current[0].'@build-time' = formattedBuildTime
        resourceFile.withPrintWriter { writer -> new XmlNodePrinter(writer).print(releases) }
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
        assert releasesFile.exists()
        def releases = new XmlParser().parse(releasesFile)
        def nextRelease = releases.next[0]
        assert nextRelease && nextRelease.'@version'
        def thisRelease = nextRelease.'@version'
        nextRelease.@version = calculateNextVersion(thisRelease)
        def currentRelease = releases.current[0]
        assert currentRelease
        currentRelease + {
            release(version: thisRelease, "build-time": formattedBuildTime)
        }
        releasesFile.withWriter { writer -> new XmlNodePrinter(new IndentPrinter(writer, "    ")).print(releases) }
    }

    private String getFormattedBuildTime() {
        return new SimpleDateFormat("yyyyMMddHHmmssZ").format(project.version.buildTime)
    }
}
