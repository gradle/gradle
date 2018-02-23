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

package org.gradle.test.fixtures.file

import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.GroovyRuntimeUtil
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class CleanupTestDirectoryExtension extends AbstractAnnotationDrivenExtension<CleanupTestDirectory> {
    @Override
    void visitSpecAnnotation(CleanupTestDirectory annotation, SpecInfo spec) {
        spec.features.each { FeatureInfo feature ->
            feature.addIterationInterceptor(new FailureCleanupInterceptor(annotation.fieldName()))
        }
    }

    private static class FailureCleanupInterceptor implements IMethodInterceptor {
        final String fieldName

        FailureCleanupInterceptor(String fieldName) {
            this.fieldName = fieldName
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            def noCleanupOnErrorListener = new AbstractRunListener() {
                @Override
                void error(ErrorInfo error) {
                    TestDirectoryProvider provider = GroovyRuntimeUtil.getProperty(invocation.instance, fieldName) as TestDirectoryProvider
                    provider.suppressCleanup()
                }
            }
            def spec = invocation.spec
            while (spec != null) {
                spec.addListener(noCleanupOnErrorListener)
                spec = spec.subSpec
            }
            invocation.proceed()
        }
    }
}
