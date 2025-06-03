/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.notify

import com.google.common.base.Predicate
import com.google.common.collect.Sets
import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.jspecify.annotations.Nullable

@CompileStatic
class BuildOperationNotificationFixture extends BuildOperationsFixture {

    BuildOperationNotificationFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        super(executer, projectDir)
    }

    def op(Class<?> detailsClass, Map<String, String> details = [:]) {
        def found = all().findAll { op ->
            return op.detailsType != null && detailsClass.isAssignableFrom(op.detailsType) && op.details.subMap(details.keySet()) == details
        }
        assert found.size() == 1
        return found.first()
    }

    def ops(Class<?> detailsClass, Map<String, String> details = [:]) {
        def found = all().findAll { op ->
            return op.detailsType != null && detailsClass.isAssignableFrom(op.detailsType) && op.details.subMap(details.keySet()) == details
        }
        return found
    }

    void started(Class<?> type, Predicate<? super Map<String, ?>> payloadTest) {
        has(true, type, payloadTest)
    }

    void started(Class<?> type, Map<String, ?> payload = null) {
        has(true, type, (Map) payload)
    }

    void finished(Class<?> type, Map<String, ?> payload = null) {
        has(false, type, (Map) payload)
    }

    void has(boolean started, Class<?> type, Map<String, ?> payload) {
        has(started, type, payload ? payloadEquals(payload) : null)
    }

    private static Predicate<? super Map<String, ?>> payloadEquals(Map<String, ?> expectedPayload) {
        { Map<String, ?> actualPayload ->
            def present = Sets.intersection(actualPayload.keySet(), expectedPayload.keySet())
            for (String key : present) {
                if (!testValue(expectedPayload[key], actualPayload[key])) {
                    return false
                }
            }
            true
        } as Predicate<? super Map<String, ?>>
    }

    private static boolean testValue(expectedValue, actualValue) {
        if (expectedValue instanceof Closure) {
            expectedValue.call(actualValue)
        } else if (expectedValue instanceof Predicate) {
            expectedValue.apply(actualValue)
        } else {
            expectedValue == actualValue
        }
    }

    void has(boolean started, Class<?> type, @Nullable Predicate<? super Map<String, ?>> payloadTest) {
        def typedOps = all().findAll { op ->
            if (started) {
                if (op.detailsType == null) {
                    return false
                }
                return type.isAssignableFrom(op.detailsType)
            } else {
                if (op.resultType == null) {
                    return false
                }
                return type.isAssignableFrom(op.resultType)
            }
        }

        assert typedOps.size() > 0: "no operations of type $type"

        if (payloadTest != null) {
            def tested = []
            def matchingOps = typedOps.findAll { matchingOp ->
                def toTest = started ? matchingOp.details : matchingOp.result
                tested << toTest
                payloadTest.apply(toTest)
            }

            if (matchingOps.empty) {
                throw new AssertionError("Did not find match among:\n\n ${tested.join("\n")}")
            }
        }
    }
}
