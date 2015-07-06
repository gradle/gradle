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

package org.gradle.tooling.internal.consumer.connection;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.tooling.tests.TestExecutionException;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.task.TaskOperationDescriptor;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.consumer.DefaultInternalJvmTestExecutionDescriptor;
import org.gradle.tooling.internal.consumer.DefaultInternalTestExecutionRequest;
import org.gradle.tooling.internal.consumer.converters.TaskPropertyHandlerFactory;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestExecutionDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;
import org.gradle.tooling.internal.provider.TestExecutionRequest;

import java.util.Collection;
import java.util.List;

/*
 * <p>Used for providers >= 2.5.</p>
 */
public class TestExecutionConsumerConnection extends ShutdownAwareConsumerConnection {
    private final ProtocolToModelAdapter adapter;

    public TestExecutionConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, modelMapping, adapter);
        this.adapter = adapter;
    }

    public Void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        final BuildCancellationTokenAdapter cancellationTokenAdapter = new BuildCancellationTokenAdapter(operationParameters.getCancellationToken());
        final BuildResult<Object> result = ((InternalTestExecutionConnection) getDelegate()).runTests(toInternalTestExecutionRequest(testExecutionRequest), cancellationTokenAdapter, operationParameters);
        Action<SourceObjectMapping> mapper = new TaskPropertyHandlerFactory().forVersion(getVersionDetails());
        return adapter.adapt(Void.class, result.getModel(), mapper);
    }

    InternalTestExecutionRequest toInternalTestExecutionRequest(TestExecutionRequest testExecutionRequest) {
        final Collection<TestOperationDescriptor> testOperationDescriptors = testExecutionRequest.getTestOperationDescriptors();
        final Collection<JvmTestOperationDescriptor> jvmTestOperationDescriptors = toJvmTestOperatorDescriptor(testOperationDescriptors);
        final List<InternalJvmTestExecutionDescriptor> internalJvmTestDescriptors = Lists.newArrayList();
        for (final JvmTestOperationDescriptor descriptor : jvmTestOperationDescriptors) {
            internalJvmTestDescriptors.add(new DefaultInternalJvmTestExecutionDescriptor(descriptor.getClassName(), descriptor.getMethodName(), findTaskPath(descriptor)));
        }
        InternalTestExecutionRequest internalTestExecutionRequest = new DefaultInternalTestExecutionRequest(internalJvmTestDescriptors);
        return internalTestExecutionRequest;
    }

    private Collection<JvmTestOperationDescriptor> toJvmTestOperatorDescriptor(Collection<TestOperationDescriptor> testOperationDescriptors) {
        assertOnlyJvmTestOperatorDescriptors(testOperationDescriptors);

        return Collections2.transform(testOperationDescriptors, new Function<TestOperationDescriptor, JvmTestOperationDescriptor>() {
            @Override
            public JvmTestOperationDescriptor apply(TestOperationDescriptor input) {
                return (JvmTestOperationDescriptor) input;
            }
        });
    }

    private void assertOnlyJvmTestOperatorDescriptors(Collection<TestOperationDescriptor> testOperationDescriptors) {
        for (TestOperationDescriptor testOperationDescriptor : testOperationDescriptors) {
            if (!(testOperationDescriptor instanceof JvmTestOperationDescriptor)) {
                throw new TestExecutionException("Invalid TestOperationDescriptor implementation. Only JvmTestOperationDescriptor supported.");
            }
        }
    }

    private String findTaskPath(JvmTestOperationDescriptor descriptor) {
        OperationDescriptor parent = descriptor.getParent();
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();
        }
        if (parent instanceof TaskOperationDescriptor) {
            return ((TaskOperationDescriptor) parent).getTaskPath();
        } else {
            return null;
        }
    }
}
