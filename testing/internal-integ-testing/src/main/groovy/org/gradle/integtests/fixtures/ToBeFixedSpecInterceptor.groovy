/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.reflect.ClassInspector
import org.gradle.test.fixtures.ResettableExpectations
import org.opentest4j.TestAbortedException
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

class ToBeFixedSpecInterceptor {

    private final String gradleMode

    ToBeFixedSpecInterceptor(String gradleMode) {
        this.gradleMode = gradleMode
    }

    void intercept(SpecElementInfo specElementInfo, String[] iterationMatchers) {
        if (specElementInfo instanceof SpecInfo) {
            specElementInfo.features.forEach { interceptFeature(it, iterationMatchers) }
        } else {
            interceptFeature((FeatureInfo) specElementInfo, iterationMatchers)
        }
    }

    private void interceptFeature(FeatureInfo featureInfo, String[] iterationMatchers) {
        if (featureInfo.isParameterized()) {
            featureInfo.addInterceptor(new ToBeFixedIterationInterceptor(iterationMatchers))
        } else {
            featureInfo.getFeatureMethod().addInterceptor(new ToBeFixedInterceptor())
        }
    }

    static class UnexpectedSuccessException extends Exception {
        UnexpectedSuccessException(String gradleMode) {
            super("Expected to fail with $gradleMode, but succeeded!")
        }
    }

    private class ToBeFixedInterceptor implements IMethodInterceptor {

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (failsAsExpected(invocation, gradleMode)) {
                return
            }
            throw new UnexpectedSuccessException(gradleMode)
        }
    }

    private class ToBeFixedIterationInterceptor implements IMethodInterceptor {
        private final String[] iterationMatchers

        ToBeFixedIterationInterceptor(String[] iterationMatchers) {
            this.iterationMatchers = iterationMatchers
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            final AtomicBoolean pass = new AtomicBoolean()
            invocation.getFeature().getFeatureMethod().interceptors.add(
                0,
                new InnerIterationInterceptor(pass, iterationMatchers)
            )
            try {
                invocation.proceed()
            } catch (Throwable ex) {
                expectedFailure(ex, gradleMode)
                pass.set(true)
            }

            if (pass.get()) {
                throw new TestAbortedException("Failed as expected.")
            } else {
                throw new UnexpectedSuccessException(gradleMode)
            }
        }

        private class InnerIterationInterceptor implements IMethodInterceptor {
            private final AtomicBoolean pass
            private final String[] iterationMatchers

            InnerIterationInterceptor(AtomicBoolean pass, String[] iterationMatchers) {
                this.pass = pass
                this.iterationMatchers = iterationMatchers
            }

            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                if (GradleModeTestingPolicy.iterationMatches(iterationMatchers, invocation.iteration.displayName)) {
                    if (failsAsExpected(invocation, gradleMode)) {
                        pass.set(true)
                    }
                } else {
                    invocation.proceed()
                }
            }
        }
    }

    private static boolean failsAsExpected(IMethodInvocation invocation, String gradleMode) {
        try {
            invocation.proceed()
        } catch (Throwable ex) {
            expectedFailure(ex, gradleMode)
            ignoreCleanupAssertionsOf(invocation)
            return true
        }
        // Trigger validation failures early so they can still fail the test the usual way
        try {
            allResettableExpectationsOf(invocation.instance).forEach { expectations ->
                expectations.resetExpectations()
            }
        } catch (Throwable ex) {
            expectedFailure(ex, gradleMode)
            ignoreCleanupAssertionsOf(invocation)
            return true
        }
        return false
    }

    private static ignoreCleanupAssertionsOf(IMethodInvocation invocation) {
        def instance = invocation.instance
        if (instance instanceof AbstractIntegrationSpec) {
            instance.ignoreCleanupAssertions()
        }
        allResettableExpectationsOf(instance).forEach { expectations ->
            try {
                expectations.resetExpectations()
            } catch (Throwable error) {
                error.printStackTrace()
            }
        }
    }

    private static List<ResettableExpectations> allResettableExpectationsOf(instance) {
        allInstanceFieldsOf(instance).findResults { field ->
            try {
                def fieldValue = field.tap { accessible = true }.get(instance)
                fieldValue instanceof ResettableExpectations ? fieldValue : null
            } catch (Exception ignored) {
                null
            }
        }
    }

    private static Collection<Field> allInstanceFieldsOf(instance) {
        ClassInspector.inspect(instance.getClass()).instanceFields
    }

    private static void expectedFailure(Throwable ex, String gradleMode) {
        System.err.println("Failed with $gradleMode as expected:")
        ex.printStackTrace()
    }
}
