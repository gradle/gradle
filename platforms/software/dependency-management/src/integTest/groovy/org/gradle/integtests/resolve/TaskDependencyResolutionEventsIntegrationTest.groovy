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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class TaskDependencyResolutionEventsIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because = "broken file collection")
    def "does not generate events when task dependencies are calculated for configuration that is used as a task input"() {
        given:
        buildFile << """
            configurations {
                parent { }
                things.extendsFrom parent
                all {
                    incoming.beforeResolve { throw new RuntimeException() }
                    incoming.afterResolve { throw new RuntimeException() }
                }
            }
            dependencies {
                parent files("parent.txt")
                things files("thing.txt")
            }

            task resolveIt {
                inputs.files configurations.things // no output declared and no action -> don't resolve inputs
            }
        """

        expect:
        succeeds "resolveIt"
    }
}
