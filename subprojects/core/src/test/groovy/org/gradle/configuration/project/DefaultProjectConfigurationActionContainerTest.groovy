/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.configuration.project

import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

class DefaultProjectConfigurationActionContainerTest extends Specification {
    def container = new DefaultProjectConfigurationActionContainer()

    def "can add action to container"() {
        def action = Mock(Action)

        when:
        container.add(action)

        then:
        container.actions == [action]
    }

    def "can add action as closure"() {
        def run = false
        def action = { run = true }

        when:
        container.add(action)

        then:
        container.actions.size() == 1

        when:
        container.actions[0].execute(Stub(ProjectInternal))

        then:
        run
    }
}
