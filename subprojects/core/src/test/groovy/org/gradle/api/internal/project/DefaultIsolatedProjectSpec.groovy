/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.util.Path
import spock.lang.Specification

class DefaultIsolatedProjectSpec extends Specification {

    def "equals and hashCode are based on project identity"() {
        given:
        def identity = ProjectIdentity.forSubproject(Path.ROOT, Path.path(":sub"))
        def sameIdentity = ProjectIdentity.forSubproject(Path.ROOT, Path.path(":sub"))
        def otherIdentity = ProjectIdentity.forSubproject(Path.ROOT, Path.path(":other"))

        def subject = new DefaultIsolatedProject(stateWithIdentity(identity))

        expect:
        subject.hashCode() == identity.hashCode()
        subject == new DefaultIsolatedProject(stateWithIdentity(sameIdentity))
        subject != new DefaultIsolatedProject(stateWithIdentity(otherIdentity))
    }

    private ProjectState stateWithIdentity(ProjectIdentity identity) {
        Mock(ProjectState) {
            getIdentity() >> identity
        }
    }
}
