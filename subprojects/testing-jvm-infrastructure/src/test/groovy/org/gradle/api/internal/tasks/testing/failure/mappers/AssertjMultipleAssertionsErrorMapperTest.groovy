/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.failure.mappers

import org.assertj.core.error.MultipleAssertionsError
import org.gradle.api.internal.tasks.testing.failure.RootAssertionToFailureMapper
import org.gradle.api.tasks.testing.TestFailure
import spock.lang.Specification

class AssertjMultipleAssertionsErrorMapperTest extends Specification {
    // SUT
    def mapper = new AssertjMultipleAssertionsErrorMapper()

    // Our error being mapped
    def error = new MultipleAssertionsError(
        [
            new MockFailure(1),
            new MockFailure(2),
            new MockFailure(3)
        ]
    )

    // Simple root mapper that just wraps the assertion failure
    def rootMapper = new RootAssertionToFailureMapper() {
        @Override
        TestFailure createFailure(Throwable throwable) {
            TestFailure.fromTestAssertionFailure(throwable, null, null, null)
        }
    }

    def "maps assertion failure correctly"() {
        when:
        def mappedFailure = mapper.map(error, rootMapper)

        then:
        assert mappedFailure.causes.size() == 3
        assert mappedFailure.rawFailure instanceof MultipleAssertionsError
    }

    def "maps inner assertion failures correctly"() {
        when:
        def innerErrors = mapper.mapInnerFailures(error, rootMapper)

        then:
        innerErrors.eachWithIndex { tf, i ->
            assert tf.causes.size() == 0
            assert tf.rawFailure instanceof MockFailure
            assert tf.details.message == "${i + 1}"
        }
    }

}
