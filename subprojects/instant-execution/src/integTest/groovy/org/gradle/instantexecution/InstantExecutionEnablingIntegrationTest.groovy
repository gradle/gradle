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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

import javax.annotation.Nullable


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
        setOrClearProperty(SystemProperties.isEnabled, previousProp)
    }

    def "can enable instant execution from gradle.properties"() {

        setup:
        def previousProp = System.getProperty(SystemProperties.isEnabled)
        if (GradleContextualExecuter.isEmbedded()) {
            System.clearProperty(SystemProperties.isEnabled)
        }

        and:
        file('gradle.properties') << """
            systemProp.${SystemProperties.isEnabled}=true
        """

        and:
        def fixture = newInstantExecutionFixture()

        when:
        run 'help'

        then: 'instant execution is enabled'
        fixture.assertStateStored()

        cleanup:
        setOrClearProperty(SystemProperties.isEnabled, previousProp)
    }

    private static void setOrClearProperty(String name, @Nullable String value) {
        if (value != null) {
            System.setProperty(name, value)
        } else {
            System.clearProperty(name)
        }
    }
}
