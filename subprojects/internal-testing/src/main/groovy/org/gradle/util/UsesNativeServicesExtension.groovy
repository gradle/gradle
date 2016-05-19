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

package org.gradle.util

import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo

class UsesNativeServicesExtension extends AbstractAnnotationDrivenExtension<UsesNativeServices> {
    @Override
    void visitSpecAnnotation(UsesNativeServices annotation, SpecInfo spec) {
        spec.addSharedInitializerInterceptor(new NativeServicesInitializationInterceptor())
        spec.addInitializerInterceptor(new NativeServicesInitializationInterceptor())
        spec.addInterceptor(new NativeServicesInitializationInterceptor())
    }

    private class NativeServicesInitializationInterceptor implements IMethodInterceptor {
        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            NativeServicesTestFixture.initialize()
            invocation.proceed()
        }
    }
}
