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


import spock.lang.Specification

class DefaultIsolatedProjectSpec extends Specification {

    def "delegates equals and hashCode to project"() {
        given:
        def project = Mock(ProjectInternal)
        def subject = new DefaultIsolatedProject(project, project)

        when:
        def hashCode = subject.hashCode()
        def equality = subject.equals(new DefaultIsolatedProject(project, project))

        then:
        hashCode == 42
        equality
        1 * project.hashCode() >> 42
        1 * project.equals(project) >> true
    }
}
