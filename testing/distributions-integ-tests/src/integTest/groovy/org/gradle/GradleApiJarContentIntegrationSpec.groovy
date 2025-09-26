/*
 * Copyright 2025 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import java.util.zip.ZipFile

class GradleApiJarContentIntegrationSpec extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/19577")
    def "does not contain javax-xml apis"() {
        given:
        def entryNames = getApiJarEntryNames()
        def javaxXml = entryNames.findAll { it.startsWith("javax/xml/") }
        def orgW3c = entryNames.findAll {it.startsWith("org/w3c/")}

        expect:
        javaxXml.isEmpty()
        orgW3c.isEmpty()
    }

    private List<String> getApiJarEntryNames() {
        def gradleApiJar = getApiJarFile()
        new ZipFile(gradleApiJar).stream().map { it.name }.toList()
    }

    private File getApiJarFile() {
        groovyFile(
            "buildSrc/src/main/groovy/ApiJarGenerationTrigger.groovy",
            "class ApiJarGenerationTrigger {}"
        )
        buildFile("""
            tasks.register("gradleApiJar") {
                def version = gradle.gradleVersion
                def guh = gradle.gradleUserHomeDir
                doLast {
                    def apiJar = new File(guh, "caches/${'\$'}version/generated-gradle-jars/gradle-api-${'\$'}{version}.jar")
                    println(apiJar)
                }
            }
        """)
        succeeds("gradleApiJar", "-q")
        new TestFile(output.trim()).assertExists()
    }
}
