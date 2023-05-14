/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import java.util.zip.ZipFile

class DistributionIntegritySpec extends DistributionIntegrationSpec {

    /*
     * Integration test to verify the integrity of the dependencies. The goal is to be able to check the dependencies
     * even we assume that the Gradle binaries are compromised. Ultimately this test should run outside of the Gradle.
     */

    @Override
    String getDistributionLabel() {
        'bin'
    }

    @Override
    int getMaxDistributionSizeBytes() {
        return 125 * 1024 * 1024
    }

    @Issue(['https://github.com/gradle/gradle/issues/9990', 'https://github.com/gradle/gradle/issues/10038'])
    def "validate dependency archives"() {
        when:
        def jars = collectJars(unpackDistribution())
        then:
        jars != []

        when:
        def jarsWithDuplicateFiles = [:]
        jars.each { jar ->
            new ZipFile(jar).withCloseable {
                def names = it.entries()*.name
                def groupedNames = names.groupBy { it }
                groupedNames.each { name, all ->
                    if (all.size() > 1) {
                        def jarPath = jar.absolutePath - testDirectory.absolutePath
                        jarsWithDuplicateFiles.computeIfAbsent(jarPath, { [] }) << name
                    }
                }
            }
        }

        then:
        jarsWithDuplicateFiles == [:]
    }

    private static def collectJars(TestFile file, Collection<File> acc = []) {
        if (file.name.endsWith('.jar')) {
            acc.add(file)
        }
        if (file.isDirectory()) {
            file.listFiles().each { f -> collectJars(f, acc) }
        }
        acc
    }
}
