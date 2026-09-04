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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

import java.util.zip.ZipFile

@Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
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
    int getDistributionSizeMiB() {
        return 145
    }

    /**
     * This test verifies that the distribution does not contain any duplicate files.
     * It also verifies that there are no classes duplicated between jars in the distribution.
     * This test is not perfect, but it should catch most of the problems.
     */
    @Issue(['https://github.com/gradle/gradle/issues/9990', 'https://github.com/gradle/gradle/issues/10038'])
    def "validate dependency archives"() {
        when:
        def jars = collectJars(unpackDistribution())
        then:
        jars != []

        when:
        def jarsWithDuplicateFiles = [:]
        def classesIndex = [:] as HashMap<String, List<String>> // class name -> list of containing jars
        jars.each { jar ->
            // The ABI jars (signature stubs) purposely duplicate class entries that also live in other distro jars.
            // They are excluded from the cross-jar duplicate-class check below.
            // They are checked for intra-jar duplicate entries.
            def skipCrossJarCheck = jar.name.startsWith("gradle-public-api-")
            new ZipFile(jar).withCloseable {
                def names = it.entries()*.name
                def groupedNames = names.groupBy { it }
                groupedNames.each { name, all ->
                    if (!skipCrossJarCheck && name.endsWith(".class") && !name.endsWith("module-info.class") && !name.endsWith("package-info.class")) {
                        def containingJars = classesIndex.computeIfAbsent(name, k -> [])
                        containingJars.add(jar.name)
                    }

                    if (all.size() > 1) {
                        def jarPath = jar.absolutePath - testDirectory.absolutePath
                        jarsWithDuplicateFiles.computeIfAbsent(jarPath, { [] }) << name
                    }
                }
            }
        }

        then:
        jarsWithDuplicateFiles == [:]

        and:
        def duplicateClasses = classesIndex.findAll { it.value.size() > 1 }
        duplicateClasses.isEmpty()
    }

    /**
     * Every jar that Gradle itself produces for the distribution must carry a Maven-style
     * pom.properties at the canonical path, so Maven-aware tooling can identify the artifact.
     * Gradle jars are recognised by their file name carrying the distribution base version;
     * externally published jars bundled in the distribution (such as gradle-fileevents) keep
     * their own version and are not expected to gain a Gradle-generated pom.properties here.
     *
     * The recorded version is the full Gradle version (so permanently published milestones and
     * RCs stay identifiable), except that the per-build timestamp of nightly/snapshot builds is
     * replaced with "SNAPSHOT" (the Maven convention) to keep the file reproducible. The jar file
     * name itself always uses the base version.
     */
    def "all Gradle module jars contain a Maven pom.properties"() {
        when:
        def gradleJars = collectJars(unpackDistribution()).findAll {
            it.name.startsWith("gradle-") && it.name.endsWith("-${baseVersion}.jar")
        }

        then:
        !gradleJars.isEmpty()

        when:
        def problems = [:]
        gradleJars.each { jar ->
            def artifactId = jar.name - "-${baseVersion}.jar"
            def groupId = expectedGroupFor(artifactId)
            def entryName = "META-INF/maven/${groupId}/${artifactId}/pom.properties"
            new ZipFile(jar).withCloseable { zip ->
                def entry = zip.getEntry(entryName)
                if (entry == null) {
                    problems[jar.name] = "missing $entryName"
                    return
                }
                def properties = new Properties()
                zip.getInputStream(entry).withCloseable { properties.load(it) }
                def actual = [properties.groupId, properties.artifactId, properties.version]
                def expected = [groupId, artifactId, jarMetadataVersion]
                if (actual != expected) {
                    problems[jar.name] = "expected $expected but was $actual"
                }
            }
        }

        then:
        problems == [:]
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
