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
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo


class ToBeFixedForInstantExecutionExtension extends AbstractAnnotationDrivenExtension<ToBeFixedForInstantExecution> {

    @Override
    void visitSpec(SpecInfo spec) {
        if (GradleContextualExecuter.isInstant()) {
            spec.allFeatures.each { feature ->
                def annotation = feature.featureMethod.reflection.getAnnotation(ToBeFixedForInstantExecution)
                if (annotation != null) {
                    if (isAllBottomSpecs(annotation) || spec.bottomSpec.name in annotation.bottomSpecs()) {
                        ToBeFixedForInstantExecution.Skip skip = annotation.skip()
                        if (skip == ToBeFixedForInstantExecution.Skip.DO_NOT_SKIP) {
                            spec.addListener(new CatchFeatureFailuresRunListener(feature))
                        } else {
                            feature.skipped = true
                        }
                    }
                }
            }
        }
    }

    static boolean isAllBottomSpecs(ToBeFixedForInstantExecution annotation) {
        return annotation.bottomSpecs().length == 0
    }

    @Override
    void visitFeatureAnnotation(ToBeFixedForInstantExecution annotation, FeatureInfo feature) {
        // This override is required to satisfy spock's zealous runtime checks
    }

    /**
     * Spock spec listener that registers failure collecting method interceptors for the given feature.
     *
     * Registration happens as late as possible and to the utmost position in the stack in order to catch
     * failures without being sensible to other spock extensions that re-order interceptors nor to the place
     * of annotated spock features in hierarchies of spec classes.
     */
    private static class CatchFeatureFailuresRunListener extends AbstractRunListener {

        private final FeatureInfo feature
        private final RecordFailuresInterceptor failuresInterceptor
        private final FeatureFilterInterceptor fixturesInterceptor

        CatchFeatureFailuresRunListener(FeatureInfo feature) {
            this.feature = feature
            this.failuresInterceptor = new RecordFailuresInterceptor()
            this.fixturesInterceptor = new FeatureFilterInterceptor(feature, failuresInterceptor)
        }

        @Override
        void beforeSpec(SpecInfo spec) {
            spec.specsTopToBottom*.each { s ->
                s.setupMethods*.interceptors*.add(0, fixturesInterceptor)
                s.cleanupMethods*.interceptors*.add(0, fixturesInterceptor)
            }
        }

        @Override
        void beforeFeature(FeatureInfo featureInfo) {
            if (featureInfo == feature) {
                featureInfo.featureMethod.interceptors.add(0, failuresInterceptor)
            }
        }

        @Override
        void beforeIteration(IterationInfo iteration) {
            if (iteration.feature == feature) {
                iteration.feature.iterationInterceptors.add(0, failuresInterceptor)
            }
        }

        @Override
        void afterFeature(FeatureInfo featureInfo) {
            if (featureInfo == feature) {
                if (failuresInterceptor.failures.empty) {
                    throw new UnexpectedSuccessException()
                } else {
                    System.err.println("Failed with instant execution as expected:")
                    if (failuresInterceptor.failures.size() == 1) {
                        failuresInterceptor.failures.first().printStackTrace()
                    } else {
                        new ExpectedFailureException(failuresInterceptor.failures).printStackTrace()
                    }
                }
            }
        }
    }

    private static class FeatureFilterInterceptor implements IMethodInterceptor {

        private final FeatureInfo feature
        private final IMethodInterceptor next

        FeatureFilterInterceptor(FeatureInfo feature, IMethodInterceptor next) {
            this.feature = feature
            this.next = next
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (invocation.feature == feature) {
                next.intercept(invocation)
            } else {
                invocation.proceed()
            }
        }
    }

    private static class RecordFailuresInterceptor implements IMethodInterceptor {

        List<Throwable> failures = []

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            try {
                invocation.proceed()
            } catch (Throwable ex) {
                failures += ex
            }
        }
    }

    static class UnexpectedSuccessException extends Exception {
        UnexpectedSuccessException() {
            super("Expected to fail with instant execution, but succeeded!")
        }
    }

    static class ExpectedFailureException extends Exception {
        ExpectedFailureException(List<Throwable> failures) {
            super("Expected failure with instant execution")
            failures.each { addSuppressed(it) }
        }
    }
}
