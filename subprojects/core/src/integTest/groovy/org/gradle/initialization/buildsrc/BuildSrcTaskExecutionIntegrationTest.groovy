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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import spock.lang.Issue

class BuildSrcTaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    def "can execute a task from buildSrc from the command line"() {
        file("buildSrc/build.gradle") << """
            task something {
                doLast { }
            }
        """

        expect:
        2.times {
            run(":buildSrc:something")
            result.assertTaskExecuted(":buildSrc:something")
        }

        run(":buildSrc:jar")
        result.assertTaskExecuted(":buildSrc:jar")

        run(":buildSrc:jar")
        // Task will not run when configuration cache is enabled
    }

    def "can execute a task from nested buildSrc from the command line"() {
        createDirs("nested")
        file("settings.gradle") << """
            includeBuild("nested")
        """
        file("nested/buildSrc/build.gradle") << """
            task something {
                doLast { }
            }
        """

        expect:
        2.times {
            run(":nested:buildSrc:something")
            result.assertTaskExecuted(":nested:buildSrc:something")
        }

        run(":nested:buildSrc:jar")
        result.assertTaskExecuted(":nested:buildSrc:jar")

        run(":nested:buildSrc:jar")
        // Task will not run when configuration cache is enabled
    }

    def "can exclude a task from buildSrc from the command line"() {
        file("buildSrc/build.gradle") << """
            task something {
                doLast { }
            }
            task thing {
                dependsOn something
            }
        """

        expect:
        2.times {
            run(":buildSrc:thing", "-x", ":buildSrc:something")
            result.assertTaskExecuted(":buildSrc:thing")
            result.assertTaskNotExecuted(":buildSrc:something")
        }

        2.times {
            run("-x", ":buildSrc:jar")
            result.assertTaskNotExecuted(":buildSrc:compileJava")
            result.assertTaskNotExecuted(":buildSrc:jar")
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/23885")
    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "can exclude task from main build when buildSrc is present"() {
        file("buildSrc/build.gradle").createFile()
        createDirs("lib")
        settingsFile """
            include "lib"
        """
        buildFile """
            allprojects {
                task thing {
                    doLast {}
                }
            }
        """

        expect:
        2.times {
            run("thing", "-x", ":lib:thing")
            result.assertTaskNotExecuted(":lib:thing")
        }
    }
}
