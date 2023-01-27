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

class CompositeBuildMultipleBuildLogicIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "can have build-logic build and include build with build-logic build"() {
        def rootBuildBuildLogic = new BuildTestFile(buildA.file("build-logic"), "build-logic")
        rootBuildBuildLogic.buildFile """
            plugins {
                id 'java-gradle-plugin'
            }
        """
        rootBuildBuildLogic.settingsFile.createFile()
        buildA.settingsFile << """
            includeBuild 'build-logic'
        """

        def includedBuild = multiProjectBuild("buildB", ["project1", "project2"]) {
            includeBuild(buildA)
            settingsFile << """
                includeBuild 'build-logic'
            """
        }
        def includedBuildBuildLogic = new BuildTestFile(includedBuild.file("build-logic"), "build-logic")
        includedBuildBuildLogic.buildFile """
            plugins {
                id 'java-gradle-plugin'
            }
        """
        includedBuildBuildLogic.settingsFile.createFile()

        includeBuild(includedBuild)

        expect:
        succeeds(buildA, "help")

    }

    def "names are stable"() {
        def builds = nestedBuilds {
            buildA {
                buildLogic
            }
            buildB {
                def buildB = delegate
                buildBClosure.each { buildB.with(it) }
            }
        }
        builds.each {
            it.buildFile << """
                    subprojects {
                        apply plugin: 'java'
                    }
                    println("Configured build '\$identityPath'")
                """
        }
        def buildB = builds.find { it.rootProjectName == 'buildB' }

        when:
        succeeds(buildB, 'assemble')
        then:
        [':buildA', ':buildA:buildLogic', ':', ':buildLogic', ':nested',':nested:buildLogic'].each { buildName ->
            outputContains("Configured build '$buildName'")
        }

        where:
        buildBClosure << [
            { nested {
                includeBuild '../buildLogic'
                buildLogic
            }},
            { buildLogic },
            { includeBuild '../buildA' }
        ].permutations()

    }

    List<BuildTestFile> nestedBuilds(Closure configuration) {
        new RootNestedBuildSpec(temporaryFolder).with(configuration).nestedBuilds
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


