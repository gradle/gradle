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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.ImmutableSet;
import org.gradle.StartParameter;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;

import java.util.Collection;
import java.util.Set;

public class TestExecutionRequestAction extends SubscribableBuildAction implements InternalTestExecutionRequest {
    private final StartParameter startParameter;
    private final Set<InternalTestDescriptor> testDescriptors;
    private final Set<String> testClassNames;

    public TestExecutionRequestAction(InternalTestExecutionRequest testExecutionRequest, StartParameter startParameter, BuildClientSubscriptions clientSubscriptions) {
        super(clientSubscriptions);
        this.startParameter = startParameter;
        // Unpack the request to serialize across to the daemon
        this.testDescriptors = ImmutableSet.copyOf(testExecutionRequest.getTestExecutionDescriptors());
        this.testClassNames = ImmutableSet.copyOf(testExecutionRequest.getTestClassNames());
    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    @Override
    public Collection<String> getTestClassNames() {
        return testClassNames;
    }

    @Override
    public Collection<InternalTestDescriptor> getTestExecutionDescriptors() {
        return testDescriptors;
    }

    public InternalTestExecutionRequest getTestExecutionRequest() {
        return this;
    }
}
