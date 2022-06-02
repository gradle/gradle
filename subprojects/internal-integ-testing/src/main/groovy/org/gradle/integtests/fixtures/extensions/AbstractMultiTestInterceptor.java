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

import org.gradle.internal.Cast;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IDataDriver;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.model.DataProcessorMetadata;
import org.spockframework.runtime.model.DataProviderInfo;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.Invoker;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.MethodInfo;
import org.spockframework.runtime.model.MethodKind;
import org.spockframework.runtime.model.NameProvider;
import org.spockframework.runtime.model.SpecInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A base class for those test interceptors which execute a test multiple times.
 */
public abstract class AbstractMultiTestInterceptor extends AbstractMethodInterceptor {
    protected final Class<?> target;
    private final List<Execution> executions = new ArrayList<>();
    private final boolean runAllExecutions;

    private boolean executionsInitialized;

    protected AbstractMultiTestInterceptor(Class<?> target) {
        this(target, true);
    }

    protected AbstractMultiTestInterceptor(Class<?> target, boolean runAllExecutions) {
        this.target = target;
        this.runAllExecutions = runAllExecutions;
    }

    public void interceptFeature(FeatureInfo feature) {
        initExecutions();

        boolean featureIsParameterized = feature.isParameterized();
        if (!featureIsParameterized) {
            parameterizeFeature(feature);
        }

        NameProvider<IterationInfo> iterationNameProvider = feature.getIterationNameProvider();
        if (iterationNameProvider == null) {
            feature.setIterationNameProvider(p -> feature.getName() + " " + getCurrentExecution(p));
        } else {
            feature.setIterationNameProvider(iteration -> {
                Object[] augmentedDataValues = iteration.getDataValues();
                setDataValues(iteration, Arrays.copyOf(augmentedDataValues, augmentedDataValues.length - 1));
                String iterationName = iterationNameProvider.getName(iteration);
                setDataValues(iteration, augmentedDataValues);
                return iterationName + " " + getCurrentExecution(iteration);
            });
        }

        feature.setDataDriver((dataIterator, iterationRunner, parameters) -> {
            TestDetails testDetails = new TestDetails(feature);
            while(dataIterator.hasNext()) {
                Object[] arguments = dataIterator.next();
                Object[] actualArguments =  featureIsParameterized ? IDataDriver.prepareArgumentArray(arguments, parameters) : new Object[0];
                for (Execution execution : executions) {
                    if (execution.isTestEnabled(testDetails)) {
                        Object[] augmentedArguments = new Object[actualArguments.length + 1];
                        System.arraycopy(actualArguments, 0, augmentedArguments, 0, actualArguments.length);
                        augmentedArguments[actualArguments.length] = execution;
                        iterationRunner.runIteration(augmentedArguments);
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

    private void parameterizeFeature(FeatureInfo feature) {
        if (feature.getDataProcessorMethod() == null) {
            MethodInfo dataProcessor = new MethodInfo(new ConstantInvoker(new Object[]{"data"})) {
                @Override
                public <ANN extends Annotation> ANN getAnnotation(Class<ANN> clazz) {
                    if (clazz.equals(DataProcessorMetadata.class)) {
                        return Cast.uncheckedCast(new DataProcessorMetadata() {
                            @Override
                            public String[] dataVariables() {
                                return new String[0];
                            }

                            @Override
                            public Class<? extends Annotation> annotationType() {
                                return DataProcessorMetadata.class;
                            }
                        });
                    }
                    return null;
                }
            };
            dataProcessor.setParent(feature.getSpec());
            dataProcessor.setName("internalDataProcessor");
            dataProcessor.setKind(MethodKind.DATA_PROCESSOR);

            MethodInfo dataProviderMethod = new MethodInfo(new ConstantInvoker(Collections.singleton("data")));
            feature.setDataProcessorMethod(dataProcessor);
            DataProviderInfo dataProvider = new DataProviderInfo();
            dataProvider.setParent(feature);
            dataProvider.setDataVariables(new ArrayList<>());
            dataProvider.setPreviousDataTableVariables(new ArrayList<>());
            dataProvider.setDataProviderMethod(dataProviderMethod);
            feature.getDataProviders().add(dataProvider);
        }
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
            IterationInfo iteration = invocation.getIteration();
            Execution currentExecution = (Execution) iteration.getDataValues()[iteration.getDataValues().length - 1];
            setDataValues(iteration, Arrays.copyOf(iteration.getDataValues(), iteration.getDataValues().length - 1));
            currentExecution.assertCanExecute();
            currentExecution.before(invocation);
            invocation.proceed();
            currentExecution.after();
        }
    }

    private void setDataValues(IterationInfo iteration, Object... values) {
        try {
            Field dataValuesField = IterationInfo.class.getDeclaredField("dataValues");
            dataValuesField.setAccessible(true);
            dataValuesField.set(iteration, values);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Execution getCurrentExecution(IterationInfo iterationInfo) {
        return (Execution) iterationInfo.getDataValues()[iterationInfo.getDataValues().length - 1];
    }

    public static class ConstantInvoker implements Invoker {
        private final Object value;

        public ConstantInvoker(Object value) {
            this.value = value;
        }

        @Override
        public Object invoke(Object target, Object... arguments) throws Throwable {
            return value;
        }
    }
}
