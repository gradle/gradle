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
package org.gradle.tooling.internal.consumer;

import org.gradle.api.Incubating;
import org.gradle.tooling.TestsLauncher;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultJvmTestOperationDescriptor;
import org.gradle.tooling.events.test.internal.DefaultTestOperationDescriptor;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;

import java.util.*;

@Incubating
class DefaultTestsLauncher extends AbstractBuildLauncher<DefaultTestsLauncher> implements TestsLauncher {

    private final Set<String> testIncludePatterns = new LinkedHashSet<String>();
    private final Set<String> testExcludePatterns = new LinkedHashSet<String>();
    private final List<TestOperationDescriptor> testDescriptors = new LinkedList<TestOperationDescriptor>();

    public DefaultTestsLauncher(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters, connection);
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
    }

    private void updatePatternList() {
        operationParamsBuilder.setTestIncludePatterns(new ArrayList<String>(testIncludePatterns));
        operationParamsBuilder.setTestExcludePatterns(new ArrayList<String>(testExcludePatterns));
        operationParamsBuilder.setTestDescriptors(adaptTestDescriptors(testDescriptors));
    }

    @Override
    protected DefaultTestsLauncher getThis() {
        return this;
    }

    /**
     * Adds a selection of tests to be executed using their descriptor
     *
     * @param testDescriptors the test descriptors
     * @return this instance
     */
    @Override
    public TestsLauncher addTests(TestOperationDescriptor... testDescriptors) {
        Collections.addAll(this.testDescriptors, testDescriptors);
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher addTestsByPattern(String... patterns) {
        Collections.addAll(testIncludePatterns, patterns);
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher addJvmTestClasses(String... testClasses) {
        for (String testClass : testClasses) {
            testIncludePatterns.add(testClass + ".*");
        }
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher addJvmTestMethods(String testClass, String... methods) {
        for (String method : methods) {
            testIncludePatterns.add(testClass + "." + method);
        }
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher excludeTestsByPattern(String... patterns) {
        Collections.addAll(testExcludePatterns, patterns);
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher excludeJvmTestClasses(String... testClasses) {
        for (String testClass : testClasses) {
            testExcludePatterns.add(testClass + ".*");
        }
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher excludeJvmTestMethods(String testClass, String... methods) {
        for (String method : methods) {
            testExcludePatterns.add(testClass + "." + method);
        }
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher setAlwaysRunTests(boolean alwaysRun) {
        operationParamsBuilder.setAlwaysRunTests(alwaysRun);
        return this;
    }

    private static List<? extends InternalTestDescriptor> adaptTestDescriptors(List<TestOperationDescriptor> descriptors) {
        ArrayList<InternalTestDescriptor> converted = new ArrayList<InternalTestDescriptor>();
        for (TestOperationDescriptor descriptor : descriptors) {
            if (descriptor instanceof DefaultJvmTestOperationDescriptor) {
                convertJvmDescriptor((DefaultJvmTestOperationDescriptor) descriptor, converted);
            } else if (descriptor instanceof DefaultTestOperationDescriptor) {
                convertDescriptor((DefaultTestOperationDescriptor) descriptor, converted);
            }
        }
        return converted;
    }

    private static void convertJvmDescriptor(final DefaultJvmTestOperationDescriptor descriptor, ArrayList<InternalTestDescriptor> converted) {
        InternalJvmTestDescriptor internal = new InternalJvmTestDescriptor() {
            @Override
            public String getTestKind() {
                return descriptor.getJvmTestKind().getLabel();
            }

            @Override
            public String getSuiteName() {
                return descriptor.getSuiteName();
            }

            @Override
            public String getClassName() {
                return descriptor.getClassName();
            }

            @Override
            public String getMethodName() {
                return descriptor.getMethodName();
            }

            @Override
            public Object getId() {
                return null;
            }

            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public String getDisplayName() {
                return descriptor.getDisplayName();
            }

            @Override
            public Object getParentId() {
                return null;
            }
        };
        converted.add(internal);
    }

    private static void convertDescriptor(final DefaultTestOperationDescriptor descriptor, ArrayList<InternalTestDescriptor> converted) {
        InternalTestDescriptor internal = new InternalTestDescriptor() {
            @Override
            public Object getId() {
                return null;
            }

            @Override
            public String getName() {
                return descriptor.getName();
            }

            @Override
            public String getDisplayName() {
                return descriptor.getDisplayName();
            }

            @Override
            public Object getParentId() {
                return null;
            }
        };
        converted.add(internal);
    }
}
