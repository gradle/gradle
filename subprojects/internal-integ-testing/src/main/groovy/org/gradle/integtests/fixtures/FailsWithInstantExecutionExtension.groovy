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


class FailsWithInstantExecutionExtension extends AbstractAnnotationDrivenExtension<FailsWithInstantExecution> {

    @Override
    void visitSpec(SpecInfo spec) {
        if (!GradleContextualExecuter.isInstant()) {
            return
        }
        // This listener/interceptor gymnastic is required because of the ordering
        // of spock hooks when combining extensions, junit rules, unrolled tests and class hierarchies
        spec.allFeatures
            .findAll { it.featureMethod.reflection.getAnnotation(FailsWithInstantExecution) != null }
            .each { feature -> spec.addListener(new RunListener(feature)) }
    }

    @Override
    void visitFeatureAnnotation(FailsWithInstantExecution annotation, FeatureInfo feature) {
        // This override is required to satisfy spock's zealous runtime checks
    }

    private static class RunListener extends AbstractRunListener {

        private final MethodInterceptor featureInterceptor = new MethodInterceptor()
        private final MethodInterceptor iterationInterceptor = new MethodInterceptor()

        private final FeatureInfo feature

        RunListener(FeatureInfo feature) {
            this.feature = feature
        }

        @Override
        void beforeFeature(FeatureInfo featureInfo) {
            if (feature == featureInfo) {
                println("before feature ${feature.name}")
                feature.featureMethod.interceptors.add(0, featureInterceptor)
            }
        }

        @Override
        void beforeIteration(IterationInfo iteration) {
            if (feature == iteration.feature) {
                println("before iteration ${iteration.name}")
                feature.iterationInterceptors.add(0, iterationInterceptor)
            }
        }

        @Override
        void afterFeature(FeatureInfo featureInfo) {
            if (feature == featureInfo) {
                println("after feature ${feature.name}")
                def failures = featureInterceptor.failures + iterationInterceptor.failures
                if (failures.empty) {
                    throw new UnexpectedSuccessException()
                } else {
                    System.err.println("Failed with instant execution as expected:")
                    failures.each {
                        it.printStackTrace()
                    }
                }
            }
        }
    }

    private static class MethodInterceptor implements IMethodInterceptor {

        List<Throwable> failures = []

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            println("> intercepting ${invocation.feature.name}")
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
}
