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

import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.build.BuildTestFixture
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.test.fixtures.file.TestDirectoryProvider

class CompositeBuildBuildPathAssignmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    BuildOperationsFixture fixture = new BuildOperationsFixture(executer, temporaryFolder)

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
        executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        succeeds(includingBuild, "help")
        then:
        assignedBuildPaths ==~ [
            ':includedBuild',
            ':includedBuild:buildLogic',
            ':',
            ':buildLogic'
        ]

        extractBuildIdentifierOutput(output) ==~ [
            [path: ':', name: ':'],
            [path: ':includedBuild', name: 'includedBuild'],
            [path: ':includedBuild:buildLogic', name: 'buildLogic'],
            [path: ':buildLogic', name: 'buildLogic']
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
        executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
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

        extractBuildIdentifierOutput(output) ==~ [
            [path: ':', name: ':'],
            [path: ':buildLogic', name: 'buildLogic'],
            [path: ':includedBuild', name: 'includedBuild'],
            [path: ':includedBuild:buildLogic', name: 'buildLogic'],
            [path: ':includedBuild:nested', name: 'nested'],
            [path: ':includedBuild:nested:buildLogic', name: 'buildLogic'],
            [path: ':nested', name: 'nested'],
            [path: ':nested:buildLogic', name: 'buildLogic']
        ]
    }

    def "buildSrc is relative to its including build"() {
        def builds = nestedBuilds {
            includedBuild {
                nested
            }
            includingBuild {
                nested
                includeBuild '../includedBuild'
            }
        }


        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }
        (builds + new BuildTestFile(file('includingBuild/nested/buildSrc'), 'buildSrc')).each { build ->
            def buildSrc = build.createDir('buildSrc')
            buildSrc.createFile('settings.gradle')
            buildSrc.createFile('build.gradle')
        }

        when:
        executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        succeeds(includingBuild, 'help')
        then:
        assignedBuildPaths ==~ [
            ':includedBuild', ':includedBuild:buildSrc', ':includedBuild:nested', ':includedBuild:nested:buildSrc',
            ':', ':buildSrc', ':nested', ':nested:buildSrc', ':nested:buildSrc:buildSrc']

        extractBuildIdentifierOutput(output) ==~ [
            [path: ':', name: ':'],
            [path: ':buildSrc', name: 'buildSrc'],
            [path: ':includedBuild', name: 'includedBuild'],
            [path: ':includedBuild:buildSrc', name: 'buildSrc'],
            [path: ':includedBuild:nested', name: 'nested'],
            [path: ':includedBuild:nested:buildSrc', name: 'buildSrc'],
            [path: ':nested', name: 'nested'],
            [path: ':nested:buildSrc', name: 'buildSrc'],
            [path: ':nested:buildSrc:buildSrc', name: 'buildSrc']
        ]
    }

    def "uses the directory hierarchy to determine the build path when the builds are not nested"() {
        def builds = nestedBuilds {
            includedBuildA {
                includeBuild '../includedBuildB/nested'
            }
            includedBuildB
            nested
            includingBuild {
                includeBuild '../includedBuildB'
                includeBuild '../includedBuildA'
                includeBuild '../nested'
            }
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }
        def includedBuildBNested = createDir('includedBuildB/nested')
        new BuildTestFixture(includedBuildBNested).multiProjectBuild('includedBuildB-nested', ['project1', 'project2'])

        when:
        executer.expectDocumentedDeprecationWarning("The BuildIdentifier.getName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        succeeds(includingBuild, 'help')
        then:
        assignedBuildPaths ==~ [':', ':includedBuildB', ':includedBuildA', ':nested', ':includedBuildB:nested']

        extractBuildIdentifierOutput(output) ==~ [
            [path: ':', name: ':'],
            [path: ':includedBuildA', name: 'includedBuildA'],
            [path: ':includedBuildB', name: 'includedBuildB'],
            [path: ':includedBuildB:nested', name: 'nested'],
            [path: ':nested', name: 'nested']
        ]
    }

    def "does not resolve the path conflict when the parent build is included #laterDescription"() {
        def builds = nestedBuilds {
            includedBuildA {
                includeBuild '../includedBuildB/nested'
            }
            includedBuildB

        }

        if (includeFromNestedBuild) {
            builds.addAll(nestedBuilds {
                nested {
                    includeBuild '../includedBuildB'
                }
                includingBuild {
                    includeBuild '../includedBuildA'
                    includeBuild '../nested'
                }
            })
        } else {
            builds.addAll(nestedBuilds {
                nested
                includingBuild {
                    includeBuild '../includedBuildA'
                    includeBuild '../includedBuildB'
                    includeBuild '../nested'
                }
            })
        }
        def includingBuild = builds.find { it.rootProjectName == 'includingBuild' }
        def includedBuildBNested = createDir('includedBuildB/nested')
        new BuildTestFixture(includedBuildBNested).multiProjectBuild('includedBuildB', ['project1', 'project2'])

        when:
        fails(includingBuild, 'help')
        then:
        failure.assertHasDescription("Included build ${file('nested')} has build path :nested which is the same as included build ${file('includedBuildB/nested')}")

        where:
        laterDescription             | includeFromNestedBuild
        'later from nested build'    | true
        'later from including build' | false
    }

    private List<Object> getAssignedBuildPaths() {
        fixture.progress(BuildIdentifiedProgressDetails).findAll { it.details.buildPath }*.details*.buildPath
    }

    private void configureNestedBuild(List<BuildTestFile> builds) {
        builds.each {
            it.settingsFile << """
                gradle.projectsEvaluated {
                    // only print from the root build of the build tree to avoid duplication
                    if (gradle.owner.identityPath.path == ':') {
                        def registry = gradle.services.get(${BuildStateRegistry.name})
                        registry.visitBuilds { b ->
                            def buildId = b.buildIdentifier
                            println "Build path=" + buildId.buildPath + " name=" + buildId.name
                        }
                    }
                }
            """
            it.buildFile << """
                subprojects {
                    // The Java plugin forces the configuration of the included builds
                    apply plugin: 'java'
                }
            """
        }
    }

    private extractBuildIdentifierOutput(String output) {
        output.readLines().findAll { it.startsWith("Build path=") }.collect { line ->
            def matcher = line =~ /Build path=(.+) name=(.+)/
            assert matcher.matches()
            [path: matcher.group(1), name: matcher.group(2)]
        }
    }

    List<BuildTestFile> nestedBuilds(Closure configuration) {
        def rootSpec = new RootNestedBuildSpec(temporaryFolder)
        rootSpec.with(configuration)
        def builds = rootSpec.nestedBuilds
        configureNestedBuild(builds)
        builds
    }

    class RootNestedBuildSpec {
        private final TestDirectoryProvider temporaryFolder
        List<BuildTestFile> nestedBuilds = []

        RootNestedBuildSpec(TestDirectoryProvider temporaryFolder) {
            this.temporaryFolder = temporaryFolder
        }

        def propertyMissing(String name) {
            createBuild(name)
        }

        def methodMissing(String name, def args) {
            BuildTestFile build = createBuild(name)

            def nestedBuildSpec = new NestedBuildSpec(build, nestedBuilds)
            if (args.length == 1 && args[0] instanceof Closure) {
                nestedBuildSpec.with(args[0])
            } else {
                throw new MissingMethodException(name, getClass(), args)
            }
            return nestedBuildSpec
        }

        private BuildTestFile createBuild(String name) {
            def build = new BuildTestFile(temporaryFolder.testDirectory.file(name), name)
            new BuildTestFixture(build).multiProjectBuild(name, ["project1", "project2"])
            nestedBuilds.add(build)
            build
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

        BuildTestFile createNestedBuild(String name, Closure configuration = {}) {
            includeBuild(name)
            def nestedBuild = new BuildTestFile(build.file(name), name)
            nestedBuilds.add(nestedBuild)
            new BuildTestFixture(nestedBuild).multiProjectBuild(name, ["project1", "project2"])
            def nestedBuildSpec = new NestedBuildSpec(nestedBuild, nestedBuilds)
            nestedBuildSpec.with(configuration)
            return nestedBuild
        }
    }
}


