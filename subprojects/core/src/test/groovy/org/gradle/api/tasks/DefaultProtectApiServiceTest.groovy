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

package org.gradle.api.tasks

import org.gradle.api.Action
import org.gradle.api.internal.tasks.DefaultProtectApiService
import org.gradle.testing.internal.util.Specification
import spock.lang.Subject

@Subject(DefaultProtectApiService)
class DefaultProtectApiServiceTest extends Specification {
    def service = new DefaultProtectApiService()

    def "throws IllegalStateException when calling protected method when disallowed"() {
        given:
        def action = service.wrap(newActionThatCallSomeProtectedMethod())

        when:
        action.execute(new Object())

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "someProtectedMethod() on someObject is protected and cannot be executed under the current context"
    }

    def "doesn't throw exception when calling protected method when allowed"() {
        when:
        callSomeProtectedMethod()

        then:
        noExceptionThrown()
    }

    private Action<Object> newActionThatCallSomeProtectedMethod() {
        return new Action<Object>() {
            @Override
            void execute(Object o) {
                callSomeProtectedMethod()
            }
        }
    }

    private void callSomeProtectedMethod() {
        service.assertMethodExecutionAllowed("someProtectedMethod()", "someObject")
    }
}
