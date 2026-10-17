/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.plugins.quality.internal

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class PmdVersionDetectorTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "detects version from #artifact jar"() {
        given:
        def jar = jarWithPomProperties(artifact, version)

        expect:
        PmdVersionDetector.detect([jar]) == version

        where:
        artifact                          | version
        "net.sourceforge.pmd/pmd-core"    | "7.24.0"
        "net.sourceforge.pmd/pmd"         | "5.1.0"
        "pmd/pmd"                         | "4.3"
    }

    def "detects version from directory"() {
        given:
        def dir = temporaryFolder.createDir("classes")
        dir.file("META-INF/maven/net.sourceforge.pmd/pmd-core/pom.properties").text = "version=6.55.0"

        expect:
        PmdVersionDetector.detect([dir]) == "6.55.0"
    }

    def "uses first PMD jar on the classpath"() {
        given:
        def other = jarWithPomProperties("net.sourceforge.pmd/pmd-java", "7.0.0")
        def first = jarWithPomProperties("net.sourceforge.pmd/pmd-core", "7.0.0")
        def second = jarWithPomProperties("net.sourceforge.pmd/pmd-core", "6.55.0")

        expect:
        PmdVersionDetector.detect([other, first, second]) == "7.0.0"
    }

    def "returns null without PMD core on the classpath"() {
        given:
        def missing = temporaryFolder.file("missing.jar")
        def notAJar = temporaryFolder.file("not-a.jar") << "not a jar"
        def other = jarWithPomProperties("net.sourceforge.pmd/pmd-java", "7.0.0")

        expect:
        PmdVersionDetector.detect([missing, notAJar, other]) == null
    }

    private TestFile jarWithPomProperties(String artifact, String version) {
        def contents = temporaryFolder.createDir("contents-${UUID.randomUUID()}")
        contents.file("META-INF/maven/$artifact/pom.properties").text = "version=$version"
        return contents.zipTo(temporaryFolder.file("${artifact.replace('/', '-')}-${version}-${UUID.randomUUID()}.jar"))
    }
}
