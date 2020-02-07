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

import java.util.function.Predicate


class ToBeFixedForInstantExecutionExtension extends AbstractAnnotationDrivenExtension<ToBeFixedForInstantExecution> {

    @Override
    void visitSpec(SpecInfo spec) {
        if (GradleContextualExecuter.isInstant()) {
            spec.allFeatures.each { feature ->
                def annotation = feature.featureMethod.reflection.getAnnotation(ToBeFixedForInstantExecution)
                if (annotation != null) {
                    if (isEnabledBottomSpec(annotation.bottomSpecs(), { spec.bottomSpec.name == it })) {
                        ToBeFixedForInstantExecution.Skip skip = annotation.skip()
                        if (skip == ToBeFixedForInstantExecution.Skip.DO_NOT_SKIP) {
                            spec.addListener(new CatchFeatureFailuresRunListener(feature, annotation.iterationMatchers()))
                        } else {
                            feature.skipped = true
                        }
                    }
                }
            }
        }
    }

    static boolean isEnabledBottomSpec(String[] bottomSpecs, Predicate<String> specNamePredicate) {
        return bottomSpecs.length == 0 || bottomSpecs.any { specNamePredicate.test(it) }
    }

    static boolean iterationMatches(String[] iterationMatchers, String iterationName) {
        return isAllIterations(iterationMatchers) || iterationMatchers.any { iterationName.matches(it) }
    }

    static boolean isAllIterations(String[] iterationMatchers) {
        return iterationMatchers.length == 0
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
     *
     * This is also so that failures in setup and cleanup methods are taken into account by the annotation.
     * Unfortunately this leads to not being able to throw from inside the interceptors but instead throw
     * from outside the test execution, after setup/test/cleanup, in Spock's core, effectively stopping the
     * whole spec execution. In practice, as soon as a test doesn't behave, we stop executing subsequent tests.
     */
    private static class CatchFeatureFailuresRunListener extends AbstractRunListener {

        private final FeatureInfo feature
        private final String[] iterationMatchers
        private final RecordFailuresInterceptor failuresInterceptor
        private final FeatureFilterInterceptor fixturesInterceptor

        CatchFeatureFailuresRunListener(FeatureInfo feature, String[] iterationMatchers) {
            this.feature = feature
            this.iterationMatchers = iterationMatchers
            this.failuresInterceptor = new RecordFailuresInterceptor(iterationMatchers)
            this.fixturesInterceptor = new FeatureFilterInterceptor(feature, iterationMatchers, failuresInterceptor)
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
        void afterIteration(IterationInfo iteration) {
            super.afterIteration(iteration)
        }

        @Override
        void afterFeature(FeatureInfo featureInfo) {
            if (featureInfo == feature) {
                if (failuresInterceptor.failuresByIterations.empty) {
                    throw new UnexpectedSuccessException()
                } else {
                    def isAllIterations = isAllIterations(iterationMatchers)
                    def unexpectedSuccessIterations = failuresInterceptor.failuresByIterations.findAll {
                        it.value.isEmpty() && (isAllIterations || iterationMatches(iterationMatchers, it.key))
                    }.collect { it.key }
                    if (!unexpectedSuccessIterations.isEmpty()) {
                        throw new UnexpectedSuccessException(unexpectedSuccessIterations)
                    }
                    System.err.println("Failed with instant execution as expected:")
                    if (failuresInterceptor.failuresByIterations.size() == 1 && failuresInterceptor.failuresByIterations.values().first().size() == 1) {
                        failuresInterceptor.failuresByIterations.values().flatten().first().printStackTrace()
                    } else {
                        new ExpectedFailureException(failuresInterceptor.failuresByIterations).printStackTrace()
                    }
                }
            }
        }
    }

    private static class FeatureFilterInterceptor implements IMethodInterceptor {

        private final FeatureInfo feature
        private final String[] iterationMatchers
        private final IMethodInterceptor next

        FeatureFilterInterceptor(FeatureInfo feature, String[] iterationMatchers, IMethodInterceptor next) {
            this.feature = feature
            this.iterationMatchers = iterationMatchers
            this.next = next
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (invocation.feature == feature && (isAllIterations(iterationMatchers) || iterationMatches(iterationMatchers, invocation.iteration.name))) {
                next.intercept(invocation)
            } else {
                invocation.proceed()
            }
        }
    }

    private static class RecordFailuresInterceptor implements IMethodInterceptor {

        private final String[] iterationMatchers
        Map<String, List<Throwable>> failuresByIterations = [:]

        RecordFailuresInterceptor(String[] iterationMatchers) {
            this.iterationMatchers = iterationMatchers
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            failuresByIterations.computeIfAbsent(invocation.iteration.name, { [] })
            if (isAllIterations(iterationMatchers) || iterationMatches(iterationMatchers, invocation.iteration.name)) {
                try {
                    invocation.proceed()
                } catch (Throwable ex) {
                    failuresByIterations[invocation.iteration.name].add(ex)
                }
            } else {
                invocation.proceed()
            }
        }
    }

    static class UnexpectedSuccessException extends Exception {

        UnexpectedSuccessException() {
            super("Expected to fail with instant execution, but succeeded!")
        }

        UnexpectedSuccessException(Collection<String> succeededIterations) {
            super("Expected to fail with instant execution, but succeeded on the following iterations!\n- ${succeededIterations.join("\n- ")}")
        }
    }

    static class ExpectedFailureException extends Exception {

        ExpectedFailureException(Map<String, List<Throwable>> failuresByIterations) {
            this()
            failuresByIterations.each {
                def itEx = new ExpectedFailureException(it.key)
                itEx.setStackTrace(new StackTraceElement[0])
                if (it.value.size() == 1) {
                    itEx.initCause(it.value.first())
                } else {
                    it.value.each { itEx.addSuppressed(it) }
                }
                addSuppressed(itEx)
            }
        }

        private ExpectedFailureException(String message = "Expected failure with instant execution") {
            super(message)
        }
    }
}
