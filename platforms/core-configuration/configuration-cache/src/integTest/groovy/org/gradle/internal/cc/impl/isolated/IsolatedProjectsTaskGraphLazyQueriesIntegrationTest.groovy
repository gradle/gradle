/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsTaskGraphLazyQueriesIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def setup() {
        createDirs("a", "b")
        settingsFile """
            include("a", "b")
        """
    }

    def "project can ask whether tasks of other projects are scheduled without problems"() {
        buildFile("a/build.gradle", """
            class Lint extends DefaultTask {}
            tasks.register("t")
            tasks.register("lint", Lint)
        """)
        buildFile("b/build.gradle", """
            class Lint extends DefaultTask {}

            def tScheduled = gradle.taskGraph.isScheduled(":a:t")
            def lintScheduled = gradle.taskGraph.anyScheduled(Lint)

            tasks.register("report") {
                doLast {
                    println("t scheduled = " + tScheduled.get())
                    println("lint scheduled = " + lintScheduled.get())
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:t", ":a:lint", ":b:report")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("t scheduled = true")
        outputContains("lint scheduled = true")

        when:
        isolatedProjectsRun(":a:t", ":a:lint", ":b:report")

        then:
        fixture.assertStateLoaded()
        outputContains("t scheduled = true")
        outputContains("lint scheduled = true")

        when:
        isolatedProjectsRun(":b:report")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
        outputContains("t scheduled = false")
        outputContains("lint scheduled = false")

        when:
        isolatedProjectsRun(":b:report")

        then:
        fixture.assertStateLoaded()
        outputContains("t scheduled = false")
        outputContains("lint scheduled = false")
    }
}
