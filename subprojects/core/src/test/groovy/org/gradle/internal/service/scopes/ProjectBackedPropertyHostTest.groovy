/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import spock.lang.Specification

class ProjectBackedPropertyHostTest extends Specification {
    def "disallows read before after evaluate"() {
        def state = new ProjectStateInternal()
        def project = Stub(ProjectInternal)
        _ * project.displayName >> "<project>"
        _ * project.state >> state

        def host = new ProjectBackedPropertyHost(project)

        expect:
        host.beforeRead() == "configuration of <project> has not finished yet"
        state.toBeforeEvaluate()
        host.beforeRead() == "configuration of <project> has not finished yet"
        state.toEvaluate()
        host.beforeRead() == "configuration of <project> has not finished yet"
        state.toAfterEvaluate()
        host.beforeRead() == null
        state.configured()
        host.beforeRead() == null
    }
}
