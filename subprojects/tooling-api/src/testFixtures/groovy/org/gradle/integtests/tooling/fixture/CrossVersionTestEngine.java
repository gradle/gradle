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

import org.gradle.internal.Cast;
import org.gradle.util.GradleVersion;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryListener;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.spockframework.runtime.SpockEngine;
import org.spockframework.runtime.SpockExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class CrossVersionTestEngine extends HierarchicalTestEngine<SpockExecutionContext> {

    private static final GradleVersion MIN_LOADABLE_TAPI_VERSION = GradleVersion.version("2.6");

    private final TestEngine delegateEngine = new SpockEngine();

    @Override
    public String getId() {
        return "cross-version-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        String versionToTestAgainst = System.getProperty("org.gradle.integtest.versions");
        if (versionToTestAgainst == null) {
            return new EngineDescriptor(uniqueId, "skip");
        }
        System.setProperty("org.gradle.integtest.currentVersion", GradleVersion.current().getVersion());

        EngineDescriptor engineDescriptor = new EngineDescriptor(uniqueId, getId());
        engineDescriptor.addChild(delegateEngine.discover(discoveryRequest, uniqueId.append("classloader", "current")));

        if (isToolingApiVersionLoadable(versionToTestAgainst)) {
            ClassLoader toolingApiClassloader = ToolingApiClassLoaderProvider.getToolingApiClassloader(versionToTestAgainst);
            EngineDiscoveryRequest tapiDiscoveryRequest = new ToolingApiClassloaderDiscoveryRequest(discoveryRequest, toolingApiClassloader);
            engineDescriptor.addChild(delegateEngine.discover(tapiDiscoveryRequest, uniqueId.append("classloader", "tapi")));
        }

        return engineDescriptor;
    }

    @Override
    protected SpockExecutionContext createExecutionContext(ExecutionRequest request) {
        return new SpockExecutionContext(request.getEngineExecutionListener());
    }

    private static boolean isToolingApiVersionLoadable(String candidateTapiVersion) {
        try {
            return GradleVersion.version(candidateTapiVersion).compareTo(MIN_LOADABLE_TAPI_VERSION) >= 0;
        } catch (IllegalArgumentException e) {
            // only test current -> current for quick/partial/latest selectors for now
            return false;
        }
    }
}

class ToolingApiClassloaderDiscoveryRequest implements EngineDiscoveryRequest {

    private final EngineDiscoveryRequest delegate;
    private final List<ClassSelector> selectors = new ArrayList<ClassSelector>();

    ToolingApiClassloaderDiscoveryRequest(EngineDiscoveryRequest delegate, ClassLoader classLoader) {
        this.delegate = delegate;
        List<ClassSelector> testClasses = delegate.getSelectorsByType(ClassSelector.class);
        for (ClassSelector selector: testClasses) {
            try {
                selectors.add(DiscoverySelectors.selectClass(classLoader.loadClass(selector.getClassName())));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> selectorType) {
        if (!selectorType.isAssignableFrom(ClassSelector.class)) {
            return Collections.emptyList();
        }
        return Cast.uncheckedCast(selectors);
    }

    @Override
    public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> filterType) {
        return delegate.getFiltersByType(filterType);
    }

    @Override
    public ConfigurationParameters getConfigurationParameters() {
        return delegate.getConfigurationParameters();
    }

    public EngineDiscoveryListener getDiscoveryListener() {
        return delegate.getDiscoveryListener();
    }
}
