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
        createDirs("libInclude", "libExclude")
        settingsFile << """
            rootProject.name = 'root'
            include 'libInclude'
            include 'libExclude'
        """
        mavenRepo.module("org.include", "included", "1.3").publish()
        mavenRepo.module("org.exclude", "excluded", "2.3").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

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
                def files1 = configurations.compile
                def files2 = configurations.compile.incoming.artifactView({}).files
                def files3 = configurations.compile.incoming.artifactView({componentFilter { true }}).files
                def files4 = configurations.compile.incoming.artifactView({componentFilter { false }}).files
                def filterView = configurations.compile.incoming.artifactView({componentFilter(artifactFilter)})
                def files5 = filterView.files
                def artifacts1 = filterView.artifacts
                def files6 = filterView.artifacts.artifactFiles
                doLast {
                    def all = ['included-1.3.jar', 'excluded-2.3.jar', 'libInclude.jar', 'libExclude.jar']
                    def filtered = ['included-1.3.jar', 'libInclude.jar']
                    assert files1.collect { it.name } == all
                    assert files2.files.collect { it.name } == all
                    assert files3.collect { it.name } == all
                    assert files4.collect { it.name } == []

                    assert files5.collect { it.name } == filtered
                    assert artifacts1.collect { it.file.name } == filtered
                    assert files6.collect { it.name } == filtered
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
            def filteredView = configurations.compile.incoming.artifactView({componentFilter(artifactFilter)}).files
            def unfilteredView = configurations.compile.incoming.artifactView({componentFilter({ true })}).files

            task checkFiltered {
                inputs.files(filteredView)
                doLast {
                    assert filteredView.collect { it.name } == ['libInclude.jar']
                }
            }

            task checkUnfiltered {
                inputs.files(unfilteredView)
                doLast {
                    assert unfilteredView.collect { it.name } == ['libInclude.jar', 'libExclude.jar']
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

    def "can filter for external artifacts based on component type" () {
        given:
        buildFile << """
            dependencies {
                compile 'org.include:included:1.3'
                compile 'org.exclude:excluded:2.3'
                compile project('libInclude')
                compile project('libExclude')
            }
            def artifactFilter = { component -> component instanceof ModuleComponentIdentifier}
            def filteredView = configurations.compile.incoming.artifactView{componentFilter(artifactFilter)}.files

            task checkFiltered {
                inputs.files(filteredView)
                doLast {
                    assert filteredView.collect { it.name } == ['included-1.3.jar', 'excluded-2.3.jar']
                }
            }
        """

        when:
        succeeds "checkFiltered"

        then:
        notExecuted ":libInclude:jar"
        notExecuted ":libExclude:jar"
    }

    def "can filter for local artifacts based on component type" () {
        given:
        buildFile << """
            dependencies {
                compile 'org.include:included:1.3'
                compile 'org.exclude:excluded:2.3'
                compile project('libInclude')
                compile project('libExclude')
            }
            def artifactFilter = { component -> component instanceof ProjectComponentIdentifier}
            def filteredView = configurations.compile.incoming.artifactView{componentFilter(artifactFilter)}.files

            task checkFiltered {
                inputs.files(filteredView)
                doLast {
                    assert filteredView.collect { it.name } == ['libInclude.jar', 'libExclude.jar']
                }
            }
        """

        when:
        succeeds "checkFiltered"

        then:
        executed ":libInclude:jar"
        executed ":libExclude:jar"
    }

    def "can filer local file dependencies"() {
        given:
        buildFile << """
            dependencies {
                compile files("internalLocalLibExclude.jar")
            }

            def artifactFilter = { component ->
                println "filter applied to " + component
                false
            }
            def filteredView = configurations.compile.incoming.artifactView{componentFilter(artifactFilter)}.files

            task checkFiltered {
                inputs.files(filteredView)
                doLast {
                    assert filteredView.collect { it.name } == []
                }
            }
        """

        when:
        succeeds "checkFiltered"

        then:
        output.contains("filter applied to internalLocalLibExclude.jar")
    }

    def "transforms are not triggered for artifacts that are not accessed" () {
        given:
        buildFile << """
            def artifactType = Attribute.of('artifactType', String)

            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class Jar2Class implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    println "Jar2Class"
                    def classes = outputs.dir('classes')
                }
            }

            dependencies {
                compile project('libInclude')
                compile project('libExclude')

                registerTransform(Jar2Class) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    to.attribute(Attribute.of('artifactType', String), "class")
                }
            }
            def artifactFilter = { component -> component.projectPath == ':libInclude' }
            def filteredView = configurations.compile.incoming.artifactView {
                componentFilter(artifactFilter)
                attributes { it.attribute(artifactType, "class") }
            }.files

            task accessFiles {
                inputs.files(filteredView)
                doLast {
                    filteredView.files.collect { it.name } == ['included-1.3.jar', 'excluded-2.3.jar']
                }
            }
        """

        when:
        succeeds "accessFiles"

        then:
        notExecuted ":libExclude:jar"
        executed ":libInclude:jar"
        executedTransforms == 1
    }

    private int getExecutedTransforms() {
        output.readLines().findAll { it == "Jar2Class" }.size()
    }
}
