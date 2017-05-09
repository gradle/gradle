/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve

import com.google.common.collect.Lists
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenModule

class ResolvedArtifactOrderingIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
"""
        buildFile << """
            version = '1.0'
            repositories {
                maven { url '$mavenRepo.uri' }
            }
            configurations {
                unordered
                consumerFirst
                dependencyFirst
            }
            dependencies {
                unordered "org.test:A:1.0"
                consumerFirst "org.test:A:1.0"
                dependencyFirst "org.test:A:1.0"
            }
            configurations.consumerFirst.resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST)
            configurations.dependencyFirst.resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
"""
    }

    private void checkOrdered(List<MavenModule> ordered) {
        checkArtifacts("consumerFirst", ordered)
        checkArtifacts("dependencyFirst", Lists.reverse(ordered))
    }

    private void checkUnordered(List<MavenModule> unordered) {
        checkArtifacts("unordered", unordered)
    }

    private void checkArtifacts(String name, List<MavenModule> modules) {
        def fileNames = modules.collect({"'${it.artifactFile.name}'"}).join(',')
        buildFile << """
            task check${name} {
                doLast {
                    assert configurations.${name}.collect { it.name } == [${fileNames}]
                    assert configurations.${name}.incoming.artifactView{}.files.collect { it.name } == [${fileNames}]
                    assert configurations.${name}.incoming.artifactView{}.artifacts.collect { it.file.name } == [${fileNames}]
                }
            }
"""

        assert succeeds("check${name}")
    }

    def "artifact collection has resolved artifact files and metadata 1"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modD).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modB).dependsOn(modC).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modB, modC, modD])
    }

    def "artifact collection has resolved artifact files and metadata 2"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modC).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modB).dependsOn(modD).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modB, modD, modC])
    }

    def "artifact collection has resolved artifact files and metadata 3"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modC).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modD).dependsOn(modB).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modD, modB, modC])
    }

    def "artifact collection has resolved artifact files and metadata 4"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modD).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modB).dependsOn(modD).dependsOn(modC).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modB, modD, modC])
    }

    def "artifact collection has resolved artifact files and metadata 5"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modD).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modB).dependsOn(modC).dependsOn(modD).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modB, modC, modD])
    }

    def "artifact collection has resolved artifact files and metadata 6"() {
        when:
        def modD = mavenRepo.module("org.test", "D").publish()
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modD).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modD).dependsOn(modB).dependsOn(modC).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modD, modB, modC])
    }

    def "artifact collection has resolved artifact files and metadata cycle"() {
        when:
        def modD = mavenRepo.module("org.test", "D")
        def modC = mavenRepo.module("org.test", "C").dependsOn(modD).publish()
        def modB = mavenRepo.module("org.test", "B").dependsOn(modC).publish()
        modD.dependsOn(modB).publish()
        def modA = mavenRepo.module("org.test", "A").dependsOn(modB).publish()

        then:
        checkOrdered([modA, modB, modC, modD])
        checkUnordered([modA, modB, modC, modD])
    }
}
