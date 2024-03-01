/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectLockStatisticsIntegrationTest extends AbstractIntegrationSpec {
    def "displays project lock statistics after build finishes"() {
        createDirs("child")
        settingsFile << """
            include ':child'
        """
        buildFile << """
            apply plugin: "java"

            task wait {
                doLast {
                    sleep 2000
                }
            }

            project(':child') {
                configurations {
                    foo
                }

                dependencies {
                    foo project(':')
                }

                task blocked {
                    def foo = configurations.foo
                    doLast {
                        println foo.files
                    }
                }
            }
        """

        when:
        executer.withArguments("--parallel", "-D${DefaultWorkerLeaseService.PROJECT_LOCK_STATS_PROPERTY}")
        succeeds(":wait", ":child:blocked")

        then:
        result.assertHasPostBuildOutput("Time spent waiting on project locks")
    }
}
