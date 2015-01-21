/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.Task
import spock.lang.Specification

class PublicTaskSpecificationTest extends Specification {

    def publicTaskSpec = PublicTaskSpecification.INSTANCE

    def "task with null group is private task"() {
        given:
        def privateTask = Mock(Task)
        privateTask.getGroup() >> { null }

        when:
        def isPublic = publicTaskSpec.isSatisfiedBy(privateTask)

        then:
        !isPublic
    }

    def "task with empty group is private task"() {
        given:
        def privateTask = Mock(Task)
        privateTask.getGroup() >> { "" }

        when:
        def isPublic = publicTaskSpec.isSatisfiedBy(privateTask)

        then:
        !isPublic
    }

    def "task with non-null group is public task"() {
        given:
        def publicTask = Mock(Task)
        publicTask.getGroup() >> { 'build' }

        when:
        def isPublic = publicTaskSpec.isSatisfiedBy(publicTask)

        then:
        isPublic
    }

}
