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
                configurations.create('default')
                task jar {}
                artifacts {
                    'default' file('libInclude.jar'), { builtBy jar }
                }
            }

            project(':libExclude') {
                configurations.create('default')
                task jar {}
                artifacts {
                    'default' file('libExclude.jar'), { builtBy jar }
                }
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
                    assert configurations.compile.incoming.artifactView().getFiles().collect { it.name } == all
                    assert configurations.compile.incoming.artifactView().includingComponents({ return true } as Spec).getFiles().collect { it.name } == all
                    assert configurations.compile.incoming.artifactView().includingComponents({ return false } as Spec).getFiles().collect { it.name } == []

                    def filterView = configurations.compile.incoming.artifactView().includingComponents(artifactFilter)
                    assert filterView.getFiles().collect { it.name } == filtered
                    assert filterView.getArtifacts().collect { it.file.name } == filtered
                }
            }
"""

        expect:
        succeeds ":check"
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
            def filteredView = configurations.compile.incoming.artifactView().includingComponents(artifactFilter).files
            def unfilteredView = configurations.compile.incoming.artifactView().includingComponents({ true }).files
            
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
