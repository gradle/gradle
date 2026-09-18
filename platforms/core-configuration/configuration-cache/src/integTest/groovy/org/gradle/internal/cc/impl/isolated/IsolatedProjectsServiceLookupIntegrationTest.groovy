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

import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/39131")
class IsolatedProjectsServiceLookupIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "can look up a service in a task action of the owning project"() {
        settingsFile """
            include("a")
        """
        file("a/thing.txt").text = "content"
        buildFile("a/build.gradle", """
            tasks.register("cleanThing") {
                doLast {
                    service(FileSystemOperations).delete {
                        delete("thing.txt")
                    }
                }
            }
        """)

        when:
        isolatedProjectsRun(":a:cleanThing")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
        and:
        !file("a/thing.txt").exists()
    }

    def "looking up a service on a task of another project is reported as cross-project task access"() {
        createDirs("a")
        settingsFile """
            include("a")
        """
        buildFile """
            project(':a').tasks.register('x').get().service(ObjectFactory)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'build.gradle': line 2: Project ':' cannot access 'Project.tasks' functionality on another project ':a'")
        }

        where:
        mode << ALL_MODES
    }
}
