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
package org.gradle.integtests.fixtures.extensions;

import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IDataDriver;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.NameProvider;
import org.spockframework.runtime.model.SpecInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A base class for those test interceptors which execute a test multiple times.
 */
public abstract class AbstractMultiTestInterceptor extends AbstractMethodInterceptor {
    protected final Class<?> target;
    private final List<Execution> executions = new ArrayList<>();
    private final boolean runAllExecutions;

    private boolean executionsInitialized;

    // Yay, mutable state for storing the current execution.
    private Execution currentExecution;

    protected AbstractMultiTestInterceptor(Class<?> target) {
        this(target, true);
    }

    protected AbstractMultiTestInterceptor(Class<?> target, boolean runAllExecutions) {
        this.target = target;
        this.runAllExecutions = runAllExecutions;
    }

    public void interceptFeature(FeatureInfo feature) {
        initExecutions();

        if (!feature.isParameterized()) {
            // Treat all features as parameterized, so we can use the data driver.
            feature.setForceParameterized(true);
        }

        NameProvider<IterationInfo> iterationNameProvider = feature.getIterationNameProvider();
        if (iterationNameProvider == null) {
            feature.setIterationNameProvider(p -> feature.getName() + " " + currentExecution);
        } else {
            feature.setIterationNameProvider(iteration -> iterationNameProvider.getName(iteration) + " " + currentExecution);
        }

        feature.setDataDriver((dataIterator, iterationRunner, parameters) -> {
            TestDetails testDetails = new TestDetails(feature);
            while(dataIterator.hasNext()) {
                Object[] arguments = dataIterator.next();
                Object[] actualArguments = IDataDriver.prepareArgumentArray(arguments, parameters);
                for (Execution execution : executions) {
                    if (execution.isTestEnabled(testDetails)) {
                        currentExecution = execution;
                        try {
                            iterationRunner.runIteration(actualArguments);
                        } catch (Throwable t) {
                            currentExecution = null;
                        }

                        if (!runAllExecutions) {
                            break;
                        }
                    }
                }
            }
        });

        if (canSkipFeature(feature, executions)) {
            feature.skip(executions.toString());
            return;
        }

        feature.getIterationInterceptors().add(0, new ParameterizedFeatureMultiVersionInterceptor());
    }

    protected abstract void createExecutions();

    protected void add(Execution execution) {
        executions.add(execution);
    }

    private void initExecutions() {
        if (!executionsInitialized) {
            createExecutions();
            executions.sort((e1, e2) -> e2.getDisplayName().compareTo(e1.getDisplayName()));
            for (Execution execution : executions) {
                execution.init(target);
            }
            executionsInitialized = true;
        }
    }

    private static boolean canSkipFeature(FeatureInfo feature, List<Execution> executions) {
        TestDetails testDetails = new TestDetails(feature);
        for (Execution execution : executions) {
            if (execution.isTestEnabled(testDetails)) {
                return false;
            }
        }
        return true;
    }

    public static abstract class Execution {
        protected Class<?> target;

        final void init(Class<?> target) {
            this.target = target;
        }

        protected void before(IMethodInvocation invocation) {
        }

        protected void after() {
        }

        /**
         * Returns a display name for this execution. Used in the JUnit descriptions for test execution.
         */
        protected abstract String getDisplayName();

        /**
         * Returns true if the given test should be executed, false if it should be ignored. Default is true.
         */
        public boolean isTestEnabled(TestDetails testDetails) {
            return true;
        }

        /**
         * Checks that this execution can be executed, throwing an exception if not.
         */
        protected void assertCanExecute() {
        }
    }

    public static class TestDetails {
        private final SpecInfo spec;
        private final Method featureMethod;

        TestDetails(FeatureInfo feature) {
            this.spec = feature.getSpec().getBottomSpec();
            this.featureMethod = feature.getFeatureMethod().getReflection();
        }

        public <A extends Annotation> A getAnnotation(Class<A> type) {
            A methodAnnotation = featureMethod.getAnnotation(type);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
            return spec.getAnnotation(type);
        }

        public Annotation[] getAnnotations() {
            return featureMethod.getAnnotations();
        }
    }

    private class ParameterizedFeatureMultiVersionInterceptor extends AbstractMethodInterceptor {

        @Override
        public void interceptIterationExecution(IMethodInvocation invocation) throws Throwable {
            currentExecution.assertCanExecute();
            currentExecution.before(invocation);
            invocation.proceed();
            currentExecution.after();
        }
    }
}
