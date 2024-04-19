/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.plugins

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

class GroovyJarFileTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "parse non-Groovy file"() {
        expect:
        GroovyJarFile.parse(new File("groovy-other-2.0.5.jar")) == null
        GroovyJarFile.parse(new File("groovy-2.0.5.zip")) == null
    }

    def "parse 'groovy' Jar"(String fileName, String version, String dependencyNotation) {
        def jar = GroovyJarFile.parse(new File("$fileName"))

        expect:
        jar != null
        jar.file == new File("$fileName")
        jar.baseName == "groovy"
        jar.version == VersionNumber.parse("$version")
        !jar.groovyAll
        !jar.indy
        jar.dependencyNotation == "$dependencyNotation"

        where:
        fileName                | version | dependencyNotation
        "/lib/groovy-2.0.5.jar" | "2.0.5" | "org.codehaus.groovy:groovy:2.0.5"
        "/lib/groovy-4.0.0.jar" | "4.0.0" | "org.apache.groovy:groovy:4.0.0"
        "/lib/groovy-4.1.0.jar" | "4.1.0" | "org.apache.groovy:groovy:4.1.0"
        "/lib/groovy-5.0.2.jar" | "5.0.2" | "org.apache.groovy:groovy:5.0.2"
    }

    def "parse 'groovy-all' Jar"(String fileName, String version, String dependencyNotation) {
        def jar = GroovyJarFile.parse(new File("$fileName"))

        expect:
        jar != null
        jar.file == new File("$fileName")
        jar.baseName == "groovy-all"
        jar.version == VersionNumber.parse("$version")
        jar.groovyAll
        !jar.indy
        jar.dependencyNotation == "$dependencyNotation"

        where:
        fileName                    | version | dependencyNotation
        "/lib/groovy-all-2.0.5.jar" | "2.0.5" | "org.codehaus.groovy:groovy-all:2.0.5"
        "/lib/groovy-all-4.0.0.jar" | "4.0.0" | "org.apache.groovy:groovy-all:4.0.0"
        "/lib/groovy-all-4.1.0.jar" | "4.1.0" | "org.apache.groovy:groovy-all:4.1.0"
        "/lib/groovy-all-5.0.2.jar" | "5.0.2" | "org.apache.groovy:groovy-all:5.0.2"
    }

    def "parse indy Jar"(String fileName, String version, String dependencyNotation) {
        def jar = GroovyJarFile.parse(new File("$fileName"))

        expect:
        jar != null
        jar.file == new File("$fileName")
        jar.baseName == "groovy"
        jar.version == VersionNumber.parse("$version")
        !jar.groovyAll
        jar.indy
        jar.dependencyNotation == "$dependencyNotation"

        where:
        fileName                     | version | dependencyNotation
        "/lib/groovy-2.0.5-indy.jar" | "2.0.5" | "org.codehaus.groovy:groovy:2.0.5:indy"
        "/lib/groovy-4.0.0-indy.jar" | "4.0.0" | "org.apache.groovy:groovy:4.0.0:indy"
        "/lib/groovy-4.1.0-indy.jar" | "4.1.0" | "org.apache.groovy:groovy:4.1.0:indy"
        "/lib/groovy-5.0.2-indy.jar" | "5.0.2" | "org.apache.groovy:groovy:5.0.2:indy"
    }

    def "parse symlinked Jar"(String symlinkName, String fileName, String version, String dependencyNotation) {
        def actualFile = tmpDir.file(fileName).touch()
        def symlinkFile = tmpDir.file(symlinkName).createLink(actualFile)

        def jar = GroovyJarFile.parse(symlinkFile)

        expect:
        jar != null
        jar.file == actualFile
        jar.baseName == "groovy"
        jar.version == VersionNumber.parse("$version")
        !jar.groovyAll
        !jar.indy
        jar.dependencyNotation == "$dependencyNotation"

        where:
        symlinkName            | fileName           | version | dependencyNotation
        "Maven-groovy-2.x.jar" | "groovy-2.0.5.jar" | "2.0.5" | "org.codehaus.groovy:groovy:2.0.5"
        "Maven-groovy-4.x.jar" | "groovy-4.1.0.jar" | "4.1.0" | "org.apache.groovy:groovy:4.1.0"
        "Maven-groovy-5.x.jar" | "groovy-5.0.2.jar" | "5.0.2" | "org.apache.groovy:groovy:5.0.2"
    }

}
