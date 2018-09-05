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

package org.gradle.api.internal

import spock.lang.Subject
import spock.lang.Unroll

@Subject(CompositeMutationGuard)
class CompositeMutationGuardTest extends AbstractMutationGuardSpec {
    def guard1 = new DefaultMutationGuard()
    def guard2 = new DefaultMutationGuard()
    MutationGuard guard = new CompositeMutationGuard([guard1, guard2])

    @Unroll
    def "throws IllegalStateException when calling a disallowed method when one of the guard disallowed using #methodUnderTest(#callableClass.type)"() {
        def callable = callableClass.newInstance(this)

        when:
        ensureExecuted(guard2."$methodUnderTest"(callable))

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "${target.class.simpleName}#someProtectedMethod() on ${target.toString()} cannot be executed in the current context."

        where:
        methodUnderTest         | callableClass
        "withMutationDisabled"  | AbstractMutationGuardSpec.ActionCallingDisallowedMethod
        "whileMutationDisabled" | AbstractMutationGuardSpec.RunnableCallingDisallowedMethod
        "whileMutationDisabled" | AbstractMutationGuardSpec.FactoryCallingDisallowedMethod
    }
}
