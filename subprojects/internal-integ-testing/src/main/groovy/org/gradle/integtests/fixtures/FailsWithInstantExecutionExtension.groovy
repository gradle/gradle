/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo


class FailsWithInstantExecutionExtension extends AbstractAnnotationDrivenExtension<FailsWithInstantExecution> {

    @Override
    void visitSpecAnnotation(FailsWithInstantExecution annotation, SpecInfo spec) {
        spec.features.each { feature ->
            visitFeatureAnnotation(annotation, feature)
        }
    }

    @Override
    void visitFeatureAnnotation(FailsWithInstantExecution annotation, FeatureInfo feature) {
        if (GradleContextualExecuter.isInstant()) {
            feature.featureMethod.addInterceptor(new MethodInterceptor())
        }
    }

    private static class MethodInterceptor implements IMethodInterceptor {
        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            try {
                invocation.proceed()
                throw new UnexpectedSuccessException()
            } catch (UnexpectedSuccessException ex) {
                throw ex
            } catch (Throwable ex) {
                System.err.println("Failed with instant execution as expected:")
                ex.printStackTrace()
            }
        }
    }

    private static class UnexpectedSuccessException extends Exception {
        UnexpectedSuccessException() {
            super("Expected to fail with instant execution, but succeeded!")
        }
    }
}
