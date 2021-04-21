/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.action;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.tooling.events.test.internal.DefaultDebugOptions;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalDebugOptions;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.test.ProviderInternalJvmTestRequest;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestExecutionRequestAction extends SubscribableBuildAction {
    private final StartParameterInternal startParameter;
    private final Set<InternalTestDescriptor> testDescriptors;
    private final Set<String> classNames;
    private final Set<InternalJvmTestRequest> internalJvmTestRequests;
    private final InternalDebugOptions debugOptions;
    private final Map<String, List<InternalJvmTestRequest>> taskAndTests;

    public TestExecutionRequestAction(BuildEventSubscriptions clientSubscriptions, StartParameterInternal startParameter, Set<InternalTestDescriptor> testDescriptors, Set<String> providerClassNames, Set<InternalJvmTestRequest> internalJvmTestRequests, InternalDebugOptions debugOptions, Map<String, List<InternalJvmTestRequest>> taskAndTests) {
        super(clientSubscriptions);
        this.startParameter = startParameter;
        this.testDescriptors = testDescriptors;
        this.classNames = providerClassNames;
        this.internalJvmTestRequests = internalJvmTestRequests;
        this.debugOptions = debugOptions;
        this.taskAndTests = taskAndTests;
    }

    // Unpacks the request to serialize across to the daemon and creates instance of
    // TestExecutionRequestAction
    public static TestExecutionRequestAction create(BuildEventSubscriptions clientSubscriptions, StartParameterInternal startParameter, ProviderInternalTestExecutionRequest testExecutionRequest) {
        ImmutableSet<String> classNames = ImmutableSet.copyOf(testExecutionRequest.getTestClassNames());
        return new TestExecutionRequestAction(clientSubscriptions, startParameter,
            ImmutableSet.copyOf(testExecutionRequest.getTestExecutionDescriptors()),
            classNames,
            getInternalJvmTestRequests(testExecutionRequest, classNames),
            getDebugOptions(testExecutionRequest),
            getTaskAndTests(testExecutionRequest));
    }

    private static Set<InternalJvmTestRequest> getInternalJvmTestRequests(ProviderInternalTestExecutionRequest testExecutionRequest, Set<String> classNames) {
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = testExecutionRequest.getInternalJvmTestRequests(Collections.<InternalJvmTestRequest>emptyList());
        return ImmutableSet.copyOf(toProviderInternalJvmTestRequest(internalJvmTestRequests, classNames));
    }

    private static InternalDebugOptions getDebugOptions(ProviderInternalTestExecutionRequest testExecutionRequest) {
        return testExecutionRequest.getDebugOptions(new DefaultDebugOptions());
    }

    private static Map<String, List<InternalJvmTestRequest>> getTaskAndTests(ProviderInternalTestExecutionRequest testExecutionRequest) {
        Map<String, List<InternalJvmTestRequest>> taskAndTests = testExecutionRequest.getTaskAndTests(Collections.emptyMap());
        ImmutableMap.Builder<String, List<InternalJvmTestRequest>> builder = ImmutableMap.builder();
        for (Map.Entry<String, List<InternalJvmTestRequest>> entry : taskAndTests.entrySet()) {
            builder.put(entry.getKey(), toProviderInternalJvmTestRequest(entry.getValue(), Collections.emptyList()));
        }
        return builder.build();
    }

    private static List<InternalJvmTestRequest> toProviderInternalJvmTestRequest(Collection<InternalJvmTestRequest> internalJvmTestRequests, Collection<String> testClassNames) {
        // handle consumer < 2.7
        if (internalJvmTestRequests.isEmpty()) {
            return CollectionUtils.collect(testClassNames, new Transformer<InternalJvmTestRequest, String>() {
                @Override
                public InternalJvmTestRequest transform(String testClass) {
                    return new ProviderInternalJvmTestRequest(testClass, null);
                }
            });
        } else {
            return CollectionUtils.collect(internalJvmTestRequests, new Transformer<InternalJvmTestRequest, InternalJvmTestRequest>() {
                @Override
                public InternalJvmTestRequest transform(InternalJvmTestRequest internalTestMethod) {
                    return new ProviderInternalJvmTestRequest(internalTestMethod.getClassName(), internalTestMethod.getMethodName());
                }
            });
        }
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    @Override
    public boolean isRunTasks() {
        return true;
    }

    @Override
    public boolean isCreateModel() {
        return false;
    }

    public Set<String> getTestClassNames() {
        return classNames;
    }

    public Set<InternalJvmTestRequest> getInternalJvmTestRequests() {
        return internalJvmTestRequests;
    }

    public Set<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }

    public InternalDebugOptions getDebugOptions() {
        return debugOptions;
    }

    public Map<String, List<InternalJvmTestRequest>> getTaskAndTests() {
        return taskAndTests;
    }
}
