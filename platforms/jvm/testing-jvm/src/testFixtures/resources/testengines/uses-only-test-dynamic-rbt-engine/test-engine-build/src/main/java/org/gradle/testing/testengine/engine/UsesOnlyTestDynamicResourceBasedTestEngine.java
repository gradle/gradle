/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.engine;

import org.gradle.testing.testengine.descriptor.ResourceBasedTestDescriptor;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;

import java.util.stream.Collectors;

public class UsesOnlyTestDynamicResourceBasedTestEngine implements TestEngine {
    public static final Logger LOGGER = LoggerFactory.getLogger(UsesOnlyTestDynamicResourceBasedTestEngine.class);

    public static final String ENGINE_ID = "uses-only-test-dynamic-rbt-engine";
    public static final String ENGINE_NAME = "Resource Based Test Engine (Dynamic, uses only TEST)";

    @Override
    public String getId() {
        return ENGINE_ID;
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        LOGGER.info(() -> {
            String selectorsMsg = discoveryRequest.getSelectorsByType(DiscoverySelector.class).stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n\t", "\t", ""));
            return "Discovering tests with engine: " + uniqueId + " using selectors:\n" + selectorsMsg;
        });

        EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, ENGINE_NAME);

        EngineDiscoveryRequestResolver.builder()
            // Pass false to only create TEST descriptors, not CONTAINER_AND_TEST
            .addSelectorResolver(new ResourceBasedSelectorResolver(false))
            .build()
            .resolve(discoveryRequest, engineDescriptor);

        return engineDescriptor;
    }

    @Override
    public void execute(ExecutionRequest executionRequest) {
        LOGGER.info(() -> "Executing tests with engine: " + executionRequest.getRootTestDescriptor().getUniqueId());

        EngineExecutionListener listener = executionRequest.getEngineExecutionListener();
        executionRequest.getRootTestDescriptor().getChildren().forEach(test -> {
            if (test instanceof ResourceBasedTestDescriptor) {
                listener.executionStarted(test);
                LOGGER.info(() -> "Executing resource-based test: " + test);

                TestDescriptor dynamicTest = new AbstractTestDescriptor(
                    test.getUniqueId().append("dynamicTest", "dynamic-test"),
                    "Dynamic Test"
                ) {
                    @Override
                    public Type getType() {
                        return Type.TEST;
                    }
                };
                test.addChild(dynamicTest);
                listener.dynamicTestRegistered(dynamicTest);
                listener.executionStarted(dynamicTest);
                listener.executionFinished(dynamicTest, TestExecutionResult.successful());

                listener.executionFinished(test, TestExecutionResult.successful());
            } else {
                throw new IllegalStateException("Cannot execute test: " + test + " of type: " + test.getClass().getName());
            }
        });
    }
}
