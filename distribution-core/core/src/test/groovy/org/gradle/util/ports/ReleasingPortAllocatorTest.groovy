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

package org.gradle.util.ports

import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification


class ReleasingPortAllocatorTest extends Specification {
    def "releases ports after a test"() {
        PortAllocator delegate = Mock(PortAllocator)
        PortAllocator portAllocator = new ReleasingPortAllocator(delegate)
        Statement base = Mock(Statement)
        Description description = Mock(Description)
        def statement = portAllocator.apply(base, description)

        when:
        statement.evaluate()

        then:
        1 * base.evaluate() >> {
            portAllocator.assignPort()
            portAllocator.assignPort()
        }
        2 * delegate.assignPort()

        and:
        2 * delegate.releasePort(_)
    }
}
