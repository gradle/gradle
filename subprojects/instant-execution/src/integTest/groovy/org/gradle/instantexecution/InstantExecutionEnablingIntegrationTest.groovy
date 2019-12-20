/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.util.ToBeImplemented


class InstantExecutionEnablingIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "can enable instant execution from the command line"() {

        given:
        def fixture = newInstantExecutionFixture()

        when:
        run 'help', "-D${SystemProperties.isEnabled}=true"

        then:
        fixture.assertStateStored()
    }

    def "can enable instant execution from the client jvm"() {

        setup:
        AbstractGradleExecuter.propagateSystemProperty(SystemProperties.isEnabled)
        def previousProp = System.getProperty(SystemProperties.isEnabled)
        System.setProperty(SystemProperties.isEnabled, "true")

        and:
        def fixture = newInstantExecutionFixture()

        when:
        run 'help'

        then:
        fixture.assertStateStored()

        cleanup:
        AbstractGradleExecuter.doNotPropagateSystemProperty(SystemProperties.isEnabled)
        if (previousProp != null) {
            System.setProperty(SystemProperties.isEnabled, previousProp)
        } else {
            System.clearProperty(SystemProperties.isEnabled)
        }
    }

    @ToBeImplemented
    def "can enable instant execution from gradle.properties"() {

        given:
        file('gradle.properties') << """
            systemprop.${SystemProperties.isEnabled}=true
        """

        and:
        def fixture = newInstantExecutionFixture()

        when:
        run 'help'

        then: 'it does not enable instant execution'
        fixture.assertNoInstantExecution()
    }
}
