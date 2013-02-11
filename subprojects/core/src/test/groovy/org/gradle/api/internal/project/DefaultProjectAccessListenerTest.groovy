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

package org.gradle.api.internal.project

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/11/13
 */
class DefaultProjectAccessListenerTest extends Specification {

    private listener = new DefaultProjectAccessListener()

    def "does not configure project that is currently configured"() {
        def project = Mock(ProjectInternal)
        def state = Mock(ProjectStateInternal)

        when:
        listener.beforeRequestingTaskByPath(project)

        then:
        1 * project.getState() >> state
        1 * state.getExecuting() >> true
        0 * _
    }

    def "configures project when task is requested by path"() {
        def project = Mock(ProjectInternal)
        def state = Mock(ProjectStateInternal)

        when:
        listener.beforeRequestingTaskByPath(project)

        then:
        1 * project.getState() >> state
        1 * state.getExecuting() >> false
        1 * project.evaluate()
        0 * _
    }
}
