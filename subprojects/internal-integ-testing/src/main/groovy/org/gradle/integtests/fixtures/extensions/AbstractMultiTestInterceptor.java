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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;
import org.junit.AssumptionViolatedException;
import org.spockframework.lang.SpecInternals;
import org.spockframework.mock.runtime.MockController;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.NameProvider;
import org.spockframework.runtime.model.SpecInfo;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * A base class for those test runners which execute a test multiple times.
 */
public abstract class AbstractMultiTestInterceptor extends AbstractMethodInterceptor {
    protected final Class<?> target;
    private final List<Execution> executions = new ArrayList<>();
    private final boolean runAllExecutions;

    private boolean executionsInitialized;
    private IMethodInvocation initInvocation;

    protected AbstractMultiTestInterceptor(Class<?> target) {
        this(target, true);
    }

    protected AbstractMultiTestInterceptor(Class<?> target, boolean runAllExecutions) {
        this.target = target;
        this.runAllExecutions = runAllExecutions;
    }

    public void interceptFeature(FeatureInfo feature) {
        initExecutions();

        NameProvider<IterationInfo> iterationNameProvider = feature.getIterationNameProvider();
        if (iterationNameProvider == null) {
            feature.setName(feature.getName() + " " + executions);
        } else {
            feature.setIterationNameProvider(p -> iterationNameProvider.getName(p) + " " + executions);
        }

        if (canSkipFeature(feature.getSpec(), executions)) {
            feature.skip(executions.toString());
            return;
        }

        feature.addInterceptor(this);
        feature.addIterationInterceptor(this);
    }

    @Override
    public void interceptInitializerMethod(IMethodInvocation invocation) throws Throwable {
        this.initInvocation = invocation;
        invocation.proceed();
        if (!executions.isEmpty()) {
            executions.get(0).before(invocation);
        }
    }

    private Execution currentExecution;

    @Override
    public void interceptFeatureExecution(IMethodInvocation invocation) throws Throwable {
        IterationExceptionInterceptor iterationExceptionInterceptor = new IterationExceptionInterceptor();
        invocation.getFeature().getFeatureMethod().addInterceptor(iterationExceptionInterceptor);

        TestDetails testDetails = new FeatureTestDetails(invocation);

        for (Execution execution : executions) {
            if (!execution.isTestEnabled(testDetails)) {
                continue;
            }
            // Spock 2 does not treat a test repeatedly invoked from an interceptor as a new test iteration
            // Until we can solve this in a better way, the printout below indicates the start of an iteration with a different configuration
            if (executions.size() > 1) {
                System.out.println("\nRUNNING ITERATION [" + execution.getDisplayName() + "]\n");
            }
            if (AbstractIntegrationSpec.class.isAssignableFrom(target)) {
                ((AbstractIntegrationSpec)invocation.getInstance()).resetExecuter();
            }
            if (initInvocation != null) { // null happens when a test class contains only features with 'where' clause
                initInvocation.proceed();
            }
            currentExecution = execution;
            invocation.proceed();
            // When the current iteration fails, we abort the following executions, which is far from ideal
            // This, however, makes it evident which iteration failed in this loop
            if (!runAllExecutions || iterationExceptionInterceptor.hasThrown) {
                break;
            }
            ((MockController) ((SpecInternals) invocation.getInstance()).getSpecificationContext().getMockController()).enterScope();
        }
    }

    @Override
    public void interceptIterationExecution(IMethodInvocation invocation) throws Throwable {
        currentExecution.before(invocation);
        invocation.proceed();
        currentExecution.after();
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

    private static boolean canSkipFeature(SpecInfo spec, List<AbstractMultiTestInterceptor.Execution> executions) {
        AbstractMultiTestInterceptor.TestDetails testDetails = new SpecTestDetails(spec);
        for (AbstractMultiTestInterceptor.Execution execution : executions) {
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

    public interface TestDetails {
        /**
         * Locates the given annotation for the test. May be inherited from test class.
         */
        @Nullable
        <A extends Annotation> A getAnnotation(Class<A> type);

        Annotation[] getAnnotations();
    }

    private static class SpecTestDetails implements AbstractMultiTestInterceptor.TestDetails {
        private final SpecInfo spec;

        private SpecTestDetails(SpecInfo spec) {
            this.spec = spec;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> type) {
            return spec.getAnnotation(type);
        }

        @Override
        public Annotation[] getAnnotations() {
            return spec.getAnnotations();
        }
    }

    private static class FeatureTestDetails implements TestDetails {

        private final Class<?> instanceClass;
        private final MethodInfo featureMethod;

        FeatureTestDetails(IMethodInvocation invocation) {
            this.instanceClass = invocation.getInstance().getClass();
            this.featureMethod = invocation.getFeature().getFeatureMethod();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> type) {
            A methodAnnotation = featureMethod.getAnnotation(type);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
            return instanceClass.getAnnotation(type);
        }

        @Override
        public Annotation[] getAnnotations() {
            return featureMethod.getAnnotations();
        }
    }

    private static class IterationExceptionInterceptor implements IMethodInterceptor {

        private boolean hasThrown = false;

        @Override
        public void intercept(IMethodInvocation invocation) throws Throwable {
            try {
                invocation.proceed();
            } catch (AssumptionViolatedException e) {
                System.out.println("Skipping iteration: assumption not satisfied");
                throw e;
            } catch (Throwable t) {
                hasThrown = true;
                throw t;
            }
        }
    }

}
