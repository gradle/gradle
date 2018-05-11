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

package org.gradle.plugins.ide.tooling.r33

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import spock.lang.Issue

@ToolingApiVersion('>=3.3')
@TargetGradleVersion('>=3.3')
class BuildActionCompositeBuildCrossVersionSpec extends ToolingApiSpecification {
    def "Can run no-op build action against root of composite build"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild')
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        when:
        def result = withConnection {
            action(new NoOpBuildAction()).run()
        }

        then:
        result == "result"
    }

    @Issue("https://github.com/gradle/gradle/issues/5167")
    def "Can run no-op build action against root of composite build with substitutions"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') { 
                    dependencySubstitution { 
                        substitute module('group:name') with project(':other') 
                    } 
                }
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        when:
        def result = withConnection {
            action(new NoOpBuildAction()).run()
        }

        then:
        result == "result"
    }

    def "Can fetch build scoped models from included builds"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        when:
        def builds = withConnection {
            action(new FetchBuilds()).run()
        }

        then:
        builds.rootProject.name == ["root", "includedBuild"]
    }

    @Issue("https://github.com/gradle/gradle/issues/5167")
    def "Can fetch build scoped models from included builds with substitutions"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') { 
                    dependencySubstitution { 
                        substitute module('group:name') with project(':other') 
                    } 
                }
            """
        }
        singleProjectBuildInSubfolder("includedBuild")

        when:
        def builds = withConnection {
            action(new FetchBuilds()).run()
        }

        then:
        builds.rootProject.name == ["root", "includedBuild"]
    }

    def "Can fetch project scoped models from included builds"() {
        given:
        multiProjectBuildInRootFolder("root", ["a", "b"]) {
            settingsFile << """
                includeBuild 'includedBuild'
            """
        }
        multiProjectBuildInSubFolder("includedBuild", ["c", "d"])

        when:
        def eclipseProjects = withConnection {
            action(new FetchEclipseProjects()).run()
        }

        then:
        eclipseProjects*.name == ['root', 'a', 'b', 'includedBuild', 'c', 'd']
    }

    @Issue("https://github.com/gradle/gradle/issues/5167")
    def "Can fetch project scoped models from included builds with substitutions"() {
        given:
        multiProjectBuildInRootFolder("root", ["a", "b"]) {
            settingsFile << """
                includeBuild('includedBuild') { 
                    dependencySubstitution { 
                        substitute module('group:name') with project(':other') 
                    } 
                }
            """
        }
        multiProjectBuildInSubFolder("includedBuild", ["c", "d"])

        when:
        def eclipseProjects = withConnection {
            action(new FetchEclipseProjects()).run()
        }

        then:
        eclipseProjects*.name == ['root', 'a', 'b', 'includedBuild', 'c', 'd']
    }
}
