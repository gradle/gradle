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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ArtifactCollectionOrderingIntegrationTest extends AbstractHttpDependencyResolutionTest {

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
                compile
            }
            dependencies {
                compile "org.test:A:1.0"
            }
            
            def artifacts = configurations.compile.incoming.artifacts

            task checkArtifacts {
                doLast {
                    assert configurations.compile.collect { it.name } == ['A-1.0.jar', 'B-1.0.jar', 'C-1.0.jar', 'D-1.0.jar']
                }
            }
"""
    }

    def "artifact collection has resolved artifact files and metadata 1"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleD).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleB).dependsOn(moduleC).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata 2"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleC).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleB).dependsOn(moduleD).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata 3"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleC).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleD).dependsOn(moduleB).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata 4"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleD).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleB).dependsOn(moduleD).dependsOn(moduleC).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata 5"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleD).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleB).dependsOn(moduleC).dependsOn(moduleD).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata 6"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D").publish()
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleD).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleD).dependsOn(moduleB).dependsOn(moduleC).publish()

        then:
        succeeds "checkArtifacts"
    }

    def "artifact collection has resolved artifact files and metadata cycle"() {
        when:
        def moduleD = mavenRepo.module("org.test", "D")
        def moduleC = mavenRepo.module("org.test", "C").dependsOn(moduleD).publish()
        def moduleB = mavenRepo.module("org.test", "B").dependsOn(moduleC).publish()
        moduleD.dependsOn(moduleB).publish()
        mavenRepo.module("org.test", "A").dependsOn(moduleB).publish()

        then:
        succeeds "checkArtifacts"
    }
}
