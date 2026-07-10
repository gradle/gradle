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
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver;

import java.util.stream.Collectors;

public class FailsExecutionResourceBasedTestEngine implements TestEngine {
    public static final Logger LOGGER = LoggerFactory.getLogger(FailsExecutionResourceBasedTestEngine.class);

    public static final String ENGINE_ID = "fails-execution-rbt-engine";
    public static final String ENGINE_NAME = "Resource Based Test Engine (Fails at Execution)";

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
            .addSelectorResolver(new ResourceBasedSelectorResolver())
            .build()
            .resolve(discoveryRequest, engineDescriptor);

        return engineDescriptor;
    }

    @Override
    public void execute(ExecutionRequest executionRequest) {
        LOGGER.info(() -> "Executing tests with engine: " + executionRequest.getRootTestDescriptor().getUniqueId());

        throw new RuntimeException("Test execution failed");
    }
}
