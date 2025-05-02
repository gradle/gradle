/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.id

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class ConfigurationCacheableIdFactoryIntegrationTest extends AbstractIntegrationSpec {

    @Requires(IntegTestPreconditions.NotConfigCached)
    def "unable to create new configuration-cacheable ids after load"() {
        buildFile << """
            def factory = gradle.services.get(${ConfigurationCacheableIdFactory.name})

            task run {
                doLast {
                    def id = factory.createId()
                    println("Created new id: \$id")
                }
            }
        """

        when:
        succeeds ":run"
        then:
        executedAndNotSkipped(":run")
        outputContains("Created new id:")

        when:
        executer.withArgument("--configuration-cache")
        runAndFail ":run"

        then:
        failure.assertHasCause("Cannot create a new id after one has been loaded")
    }

}
