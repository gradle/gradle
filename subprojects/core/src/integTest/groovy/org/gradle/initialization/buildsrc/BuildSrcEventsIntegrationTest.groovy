/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class BuildSrcEventsIntegrationTest extends AbstractIntegrationSpec {
    @UnsupportedWithConfigurationCache(because = "uses buildFinished")
    def "buildSrc build finished hook is executed after running main tasks and before root build build finished hook"() {
        file("buildSrc/build.gradle") << """
            System.clearProperty("buildsrc")

            gradle.buildFinished {
                println "buildSrc finished"
                System.setProperty("buildsrc", "done")
            }
        """
        file("build.gradle") << """
            task thing {
                doLast {
                    println("running task")
                    assert System.getProperty("buildsrc") == null
                }
            }

            gradle.buildFinished {
                println "root build finished"
                assert System.getProperty("buildsrc") == "done"
            }
        """

        when:
        run()

        then:
        output.indexOf("running tasks") < output.indexOf("buildSrc finished")
        output.indexOf("buildSrc finished") < output.indexOf("root build finished")
    }

    @UnsupportedWithConfigurationCache(because = "uses buildFinished")
    def "buildSrc build finished failure is visible to root build build finished hook"() {
        file("buildSrc/build.gradle") << """
            gradle.buildFinished {
                println "buildSrc finished"
                throw new RuntimeException("broken")
            }
        """
        buildFile << """
            gradle.buildFinished { result ->
                println "root build finished"
                assert result.failure != null
            }
        """

        when:
        fails()

        then:
        outputContains("root build finished")
        failure.assertHasDescription("broken")
    }

    @UnsupportedWithConfigurationCache(because = "uses buildFinished")
    def "buildSrc build finished failure is not lost when root build finished hook fails"() {
        file("buildSrc/build.gradle") << """
            gradle.buildFinished {
                println "buildSrc finished"
                throw new RuntimeException("buildSrc")
            }
        """
        buildFile << """
            gradle.buildFinished { result ->
                println "root build finished"
                assert result.failure != null
                throw new RuntimeException("root build")
            }
        """

        when:
        fails()

        then:
        outputContains("root build finished")
        failure.assertHasDescription("buildSrc")
        failure.assertHasDescription("root build")
    }
}
