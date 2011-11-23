/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.ISkippable

class TestPreconditionInterceptor implements IMethodInterceptor {
    private final ISkippable element
    private final List<TestPrecondition> preconditions

    TestPreconditionInterceptor(ISkippable element, List<TestPrecondition> preconditions) {
        this.element = element
        this.preconditions = preconditions
    }

    void intercept(IMethodInvocation invocation) {
        for (requirement in preconditions) {
            if (!requirement.fulfilled) {
                element.skipped = true
                return
            }
        }
        invocation.proceed()
    }
}
