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

package org.gradle.integtests.tooling.fixture;

import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.spockframework.runtime.SpockEngine;


public class CrossVersionTestEngine implements TestEngine {

    private final SpockEngine spockEngine = new SpockEngine();

    @Override
    public String getId() {
        return "cross-version-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            String toolingApiToLoad = System.getProperty("org.gradle.integtest.tooling-api-to-load");
            if (toolingApiToLoad != null) {
                ClassLoader toolingApiClassloader = ToolingApiClassLoaderProvider.getToolingApiClassloader(toolingApiToLoad);
                Thread.currentThread().setContextClassLoader(toolingApiClassloader);
            }
            return spockEngine.discover(discoveryRequest, uniqueId);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Override
    public void execute(ExecutionRequest request) {
        spockEngine.execute(request);
    }

}
