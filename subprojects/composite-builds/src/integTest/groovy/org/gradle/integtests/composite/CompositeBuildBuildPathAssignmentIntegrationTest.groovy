/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.test.fixtures.file.TestDirectoryProvider

class CompositeBuildBuildPathAssignmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "can have buildLogic build and include build with buildLogic build"() {
        def builds = nestedBuilds {
            includedBuild {
                buildLogic
            }
            includingBuild {
                buildLogic
                includeBuild '../includedBuild'
            }
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }

        when:
        succeeds(includingBuild, "help")
        then:
        assignedBuildPaths ==~ [
            ':includedBuild',
            ':includedBuild:buildLogic',
            ':',
            ':buildLogic'
        ]
    }

    def "build paths are selected based on the directory hierarchy"() {
        def builds = nestedBuilds {
            includedBuild {
                buildLogic
                nested {
                    buildLogic
                    includeBuild '../buildLogic'
                }
            }
            includingBuild {
                includeBuild '../includedBuild'
                buildLogic
                nested {
                    includeBuild '../buildLogic'
                    buildLogic
                }
            }
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }

        when:
        succeeds(includingBuild, 'help')
        then:
        assignedBuildPaths ==~ [
            ':includedBuild',
            ':includedBuild:buildLogic',
            ':includedBuild:nested',
            ':includedBuild:nested:buildLogic',
            ':',
            ':buildLogic',
            ':nested',
            ':nested:buildLogic'
        ]
    }

    private List<Object> getAssignedBuildPaths() {
        output.lines().findAll { it.startsWith("Configured build '") }.collect { it.substring(18, it.length() - 1) }
    }

    private void configureBuildConfigurationOutput(List<BuildTestFile> builds) {
        builds.each {
            it.buildFile << """
                    subprojects {
                        apply plugin: 'java'
                    }
                    println("Configured build '\$identityPath'")
                """
        }
    }

    List<BuildTestFile> nestedBuilds(Closure configuration) {
        def builds = new RootNestedBuildSpec(temporaryFolder).with(configuration).nestedBuilds
        configureBuildConfigurationOutput(builds)
        builds
    }

    class RootNestedBuildSpec {
        private final TestDirectoryProvider temporaryFolder
        List<BuildTestFile> nestedBuilds = []

        RootNestedBuildSpec(TestDirectoryProvider temporaryFolder) {
            this.temporaryFolder = temporaryFolder
        }

        def methodMissing(String name, def args) {
            def build = new BuildTestFile(temporaryFolder.testDirectory.file(name), name)
            new BuildTestFixture(build).multiProjectBuild(name, ["project1", "project2"])
            nestedBuilds.add(build)

            def nestedBuildSpec = new NestedBuildSpec(build, nestedBuilds)
            if (args.length == 1 && args[0] instanceof Closure) {
                nestedBuildSpec.with(args[0])
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
            return nestedBuildSpec
        }
    }

    class NestedBuildSpec {
        private final BuildTestFile build
        private final List<BuildTestFile> nestedBuilds

        NestedBuildSpec(BuildTestFile build, nestedBuilds) {
            this.nestedBuilds = nestedBuilds
            this.build = build
        }

        def includeBuild(String path) {
            build.settingsFile << """
                includeBuild '$path'
            """
        }

        def propertyMissing(String name) {
            createNestedBuild(name)
        }

        def methodMissing(String name, def args) {
            if (args.length == 1 && args[0] instanceof Closure) {
                return createNestedBuild(name, args[0])
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
        }

        NestedBuildSpec createNestedBuild(String name, Closure configuration = {}) {
            includeBuild(name)
            def nestedBuild = new BuildTestFile(build.file(name), name)
            nestedBuilds.add(nestedBuild)
            new BuildTestFixture(nestedBuild).multiProjectBuild(name, ["project1", "project2"])
            def nestedBuildSpec = new NestedBuildSpec(nestedBuild, nestedBuilds)
            nestedBuildSpec.with(configuration)
            return nestedBuildSpec
        }
    }
}


