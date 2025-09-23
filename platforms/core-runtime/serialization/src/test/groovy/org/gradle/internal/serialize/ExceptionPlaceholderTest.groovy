/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.serialize

import org.gradle.internal.exceptions.MultiCauseException
import spock.lang.Specification

class ExceptionPlaceholderTest extends Specification {
    class MultipleFailuresException extends Exception {
        @SuppressWarnings("unused")
        List<Throwable> failures
    }

    class MultipleSpecificFailuresException extends Exception {
        @SuppressWarnings("unused")
        List<RuntimeException> failures
    }

    class MultipleWildcardFailuresException extends Exception {
        @SuppressWarnings("unused")
        List<? extends Throwable> failures
    }

    class MultipleSpecificWildcardFailuresException extends Exception {
        @SuppressWarnings("unused")
        List<? extends RuntimeException> failures
    }

    class GenericThrowableFailuresException<T extends Throwable> extends Exception {
        @SuppressWarnings("unused")
        List<T> failures
    }

    class NotThrowableFailuresException extends Exception {
        @SuppressWarnings("unused")
        List<String> failures
    }

    class GenericObjectFailuresException<T> extends Exception {
        @SuppressWarnings("unused")
        List<T> failures
    }

    def "gets multi-cause method #methodName for #clazz"() {
        given:
        def method = ExceptionPlaceholder.findCandidateGetCausesMethod(
            clazz as Class<? extends Throwable>
        )
        expect:
        method?.name == methodName

        where:
        clazz                                     | methodName
        Throwable                                 | null
        MultiCauseException                       | "getCauses"
        MultipleFailuresException                 | "getFailures"
        MultipleSpecificFailuresException         | "getFailures"
        MultipleWildcardFailuresException         | "getFailures"
        MultipleSpecificWildcardFailuresException | "getFailures"
        GenericThrowableFailuresException         | "getFailures"
        NotThrowableFailuresException             | null
        GenericObjectFailuresException            | null
    }
}
