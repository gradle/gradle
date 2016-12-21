/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ArtifactFilterIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'libInclude'
            include 'libExclude'
        """
        mavenRepo.module("org.include", "included", "1.3").publish()
        mavenRepo.module("org.exclude", "excluded", "2.3").publish()

        buildFile << """
            project(':libInclude') {
                apply plugin: 'java'
            }

            project(':libExclude') {
                apply plugin: 'java'
            }

            configurations {
                compile
            }
"""
    }

    def "can filter artifacts based on component id"() {
        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'org.include:included:1.3'
                compile 'org.exclude:excluded:2.3'
                compile project('libInclude')
                compile project('libExclude')
            }
            def artifactFilter = { component ->
                if (component instanceof ProjectComponentIdentifier) {
                    return component.projectPath == ':libInclude'
                }
                assert component instanceof ModuleComponentIdentifier
                return component.group == 'org.include'
            }
            
            task check {
                doLast {
                    def all = ['included-1.3.jar', 'excluded-2.3.jar', 'libInclude.jar', 'libExclude.jar']
                    def filtered = ['included-1.3.jar', 'libInclude.jar']
                    assert configurations.compile.collect { it.name } == all
                    assert configurations.compile.incoming.getFiles([:], { return true } as Spec).collect { it.name } == all
                    assert configurations.compile.incoming.getFiles([:], { return false } as Spec).collect { it.name } == []
                    assert configurations.compile.incoming.getFiles([:], artifactFilter).collect { it.name } == filtered
                }
            }
"""

        expect:
        succeeds "check"
    }

    def "does not build project components excluded from view"() {
        given:
        buildFile << """
            dependencies {
                compile project('libInclude')
                compile project('libExclude')
            }
            def artifactFilter = { component ->
                assert component instanceof ProjectComponentIdentifier
                return component.projectPath == ':libInclude'
            }
            def filteredView = configurations.compile.incoming.getFiles([:], artifactFilter)
            def unfilteredView = configurations.compile.incoming.getFiles([:], { true })
            
            task checkFiltered {
                inputs.files(filteredView)
                doLast {
                    assert inputs.files.collect { it.name } == ['libInclude.jar']
                }
            }

            task checkUnfiltered {
                inputs.files(unfilteredView)
                doLast {
                    assert inputs.files.collect { it.name } == ['libInclude.jar', 'libExclude.jar']
                }
            }
        """

        when:
        succeeds "checkUnfiltered"

        then:
        executed ":libInclude:jar", ":libExclude:jar"

        when:
        succeeds "checkFiltered"

        then:
        executed ":libInclude:jar"
        notExecuted ":libExclude:jar"
    }
}
