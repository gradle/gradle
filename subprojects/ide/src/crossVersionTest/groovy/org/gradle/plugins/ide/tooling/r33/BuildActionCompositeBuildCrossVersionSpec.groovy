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

/**
 * Tests for the tooliing API, which check fetching models and running actions using different
 * combinations of versions of the TAPI and Gradle.
 *
 * Support for clients using a tooling API version older than 3.0 was removed in Gradle 5.0, so
 * there is a class-level lower bound for both versions.
 *
 * In addition, in version 6.6 the deprecated {@code DependencySubstitutions#with(ComponentSelector)} method
 * was removed, to be replaced by {@code #using(ComponentSelector)} so there are 2 versions of tests
 * present using either of those methods - pre and post Gradle 6.6.  And when the TAPI or Gradel versions
 * are overriden on the method level, you have to be sure to copy the lower bound down to the method level
 * when setting a new upper bound, otherwise the lower bound is lifted, as the method level annotations
 * replace the class level ones.
 */
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
    @ToolingApiVersion('>=3.3 <=6.6')
    @TargetGradleVersion(">=3.3 <8.0")
    def "Can run no-op build action against root of composite build with substitutions with Gradle 6.6 or earlier"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') with project(':')
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

    @Issue("https://github.com/gradle/gradle/issues/5167")
    @ToolingApiVersion('>6.6')
    @TargetGradleVersion('>6.6')
    def "Can run no-op build action against root of composite build with substitutions after Gradle 6.6"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') using project(':')
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
    @ToolingApiVersion('>=3.3 <=6.6')
    @TargetGradleVersion(">=3.3 <8.0")
    def "Can fetch build scoped models from included builds with substitutions with Gradle 6.6 or earlier"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') with project(':')
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

    @Issue("https://github.com/gradle/gradle/issues/5167")
    @ToolingApiVersion('>6.6')
    @TargetGradleVersion('>6.6')
    def "Can fetch build scoped models from included builds with substitutions after Gradle 6.6"() {
        given:
        singleProjectBuildInRootFolder("root") {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') using project(':')
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
    @ToolingApiVersion('>=3.3 <=6.6')
    @TargetGradleVersion(">=3.3 <8.0")
    def "Can fetch project scoped models from included builds with substitutions with Gradle 6.6 or earlier"() {
        given:
        multiProjectBuildInRootFolder("root", ["a", "b"]) {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') with project(':')
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

    @Issue("https://github.com/gradle/gradle/issues/5167")
    @ToolingApiVersion('>6.6')
    @TargetGradleVersion('>6.6')
    def "Can fetch project scoped models from included builds with substitutions after Gradle 6.6"() {
        given:
        multiProjectBuildInRootFolder("root", ["a", "b"]) {
            settingsFile << """
                includeBuild('includedBuild') {
                    dependencySubstitution {
                        substitute module('group:name') using project(':')
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
