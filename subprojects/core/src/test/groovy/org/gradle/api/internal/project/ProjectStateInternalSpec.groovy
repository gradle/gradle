/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.ProjectConfigurationException
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

class ProjectStateInternalSpec extends Specification {

    def "to string representation"() {
        expect:
        stateString {} == "NOT EXECUTED"
        stateString { toBeforeEvaluate() } == "EXECUTING"
        stateString { toBeforeEvaluate(); toEvaluate() } == "EXECUTING"
        stateString { toBeforeEvaluate(); toEvaluate(); toAfterEvaluate() } == "EXECUTING"
        stateString { configured() } == "EXECUTED"
        stateString { failed(new ProjectConfigurationException("bang", [])); configured() } == "FAILED (bang)"
    }

    String stateString(@DelegatesTo(ProjectStateInternal) Closure closure) {
        def state = ConfigureUtil.configure(closure, new ProjectStateInternal())
        def matcher = state.toString() =~ /^project state '(.*?)'$/
        assert matcher
        matcher[0][1]
    }

}
