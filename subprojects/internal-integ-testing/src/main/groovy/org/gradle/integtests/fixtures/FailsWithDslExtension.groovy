/*
 * Copyright 2020 the original author or authors.
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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTestInterceptor
import org.gradle.test.fixtures.dsl.GradleDsl
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo

@CompileStatic
class FailsWithDslExtension implements IAnnotationDrivenExtension<FailsWithDsl> {
    @Override
    void visitFeatureAnnotation(FailsWithDsl annotation, FeatureInfo feature) {
        def dsl = annotation.value()
        feature.featureMethod.addInterceptor(new FailsWithDslInterceptor(dsl))
    }

    private static class FailsWithDslInterceptor implements IMethodInterceptor {
        private final GradleDsl dsl

        FailsWithDslInterceptor(GradleDsl dsl) {
            this.dsl = dsl
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (PolyglotDslTestInterceptor.currentDsl == dsl) {
                try {
                    invocation.proceed()
                } catch (Throwable err) {
                    println("Test failed with DSL ${dsl.languageCodeName} as expected : ${err.message}")
                    return
                }
                throw new UnexpectedTestPassed(dsl)
            } else {
                invocation.proceed()
            }
        }
    }

    private static class UnexpectedTestPassed extends AssertionError {
        UnexpectedTestPassed(GradleDsl dsl) {
            super((Object) "Expected test to fail with ${dsl.languageCodeName} DSL but it passed")
        }
    }
}
