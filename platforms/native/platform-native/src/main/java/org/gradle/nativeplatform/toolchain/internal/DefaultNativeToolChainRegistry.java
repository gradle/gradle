/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.logging.text.DiagnosticsVisitor;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultNativeToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<NativeToolChain> implements NativeToolChainRegistryInternal {
    private final Map<String, Class<? extends NativeToolChain>> registeredDefaults = new LinkedHashMap<String, Class<? extends NativeToolChain>>();
    private final List<NativeToolChainInternal> searchOrder = new ArrayList<NativeToolChainInternal>();

    public DefaultNativeToolChainRegistry(Instantiator instantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        super(NativeToolChain.class, instantiator, instantiator, collectionCallbackActionDecorator);
        whenObjectAdded(new Action<NativeToolChain>() {
            @Override
            public void execute(NativeToolChain toolChain) {
                searchOrder.add((NativeToolChainInternal) toolChain);
            }
        });
        whenObjectRemoved(new Action<NativeToolChain>() {
            @Override
            public void execute(NativeToolChain toolChain) {
                searchOrder.remove(toolChain);
            }
        });
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(NativeToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    @Override
    public void registerDefaultToolChain(String name, Class<? extends NativeToolChain> type) {
        registeredDefaults.put(name, type);
    }

    @Override
    public void addDefaultToolChains() {
        for (String name : registeredDefaults.keySet()) {
            create(name, registeredDefaults.get(name));
        }
    }

    @Override
    public NativeToolChain getForPlatform(NativePlatform targetPlatform) {
        return getForPlatform(NativeLanguage.ANY, (NativePlatformInternal) targetPlatform);
    }

    @Override
    public NativeToolChainInternal getForPlatform(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
        for (NativeToolChainInternal toolChain : searchOrder) {
            if (toolChain.select(sourceLanguage, targetMachine).isAvailable()) {
                return toolChain;
            }
        }

        // No tool chains can build for this platform. Assemble a description of why
        Map<String, PlatformToolProvider> candidates = new LinkedHashMap<String, PlatformToolProvider>();
        for (NativeToolChainInternal toolChain : searchOrder) {
            candidates.put(toolChain.getDisplayName(), toolChain.select(sourceLanguage, targetMachine));
        }

        if (!NativeLanguage.ANY.equals(sourceLanguage) && candidates.values().stream().allMatch(it -> !it.isSupported())) {
            return new UnsupportedNativeToolChain(new UnsupportedToolChainDescription(sourceLanguage, targetMachine, candidates));
        }
        return new UnavailableNativeToolChain(new UnavailableToolChainDescription(sourceLanguage, targetMachine, candidates));
    }


    private abstract static class AbstractUnavailabilityToolChainSearchDescription implements ToolSearchResult {
        private final NativeLanguage sourceLanguage;
        private final NativePlatform targetPlatform;
        private final Map<String, PlatformToolProvider> candidates;

        private AbstractUnavailabilityToolChainSearchDescription(NativeLanguage sourceLanguage, NativePlatform targetPlatform, Map<String, PlatformToolProvider> candidates) {
            this.sourceLanguage = sourceLanguage;
            this.targetPlatform = targetPlatform;
            this.candidates = candidates;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(DiagnosticsVisitor visitor) {
            String verb = sourceLanguage == NativeLanguage.ANY ? "build" : "build " + sourceLanguage;
            visitor.node(String.format("No tool chain %s to %s for %s", getUnavailabilityReason(), verb, targetPlatform.getDisplayName()));
            visitor.startChildren();
            for (Map.Entry<String, PlatformToolProvider> entry : candidates.entrySet()) {
                visitor.node(entry.getKey());
                visitor.startChildren();
                entry.getValue().explain(visitor);
                visitor.endChildren();
            }
            if (candidates.isEmpty()) {
                visitor.node("No tool chain plugin applied.");
            }
            visitor.endChildren();
        }

        protected abstract String getUnavailabilityReason();
    }

    private static class UnavailableToolChainDescription extends AbstractUnavailabilityToolChainSearchDescription {
        public UnavailableToolChainDescription(NativeLanguage sourceLanguage, NativePlatform targetPlatform, Map<String, PlatformToolProvider> candidates) {
            super(sourceLanguage, targetPlatform, candidates);
        }

        @Override
        protected String getUnavailabilityReason() {
            return "is available";
        }
    }

    private static class UnavailableNativeToolChain implements NativeToolChainInternal {
        private final ToolSearchResult failure;

        UnavailableNativeToolChain(ToolSearchResult failure) {
            this.failure = failure;
        }

        @Override
        public String getDisplayName() {
            return getName();
        }

        @Override
        public String getName() {
            return "unavailable";
        }

        @Override
        public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure);
        }

        @Override
        public PlatformToolProvider select(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
            return select(targetMachine);
        }

        @Override
        public String getOutputType() {
            return "unavailable";
        }

        @Override
        public void assertSupported() {
            // Supported, but unavailable. Nothing to do.
        }
    }

    private static class UnsupportedToolChainDescription extends AbstractUnavailabilityToolChainSearchDescription{
        public UnsupportedToolChainDescription(NativeLanguage sourceLanguage, NativePlatform targetPlatform, Map<String, PlatformToolProvider> candidates) {
            super(sourceLanguage, targetPlatform, candidates);
        }

        @Override
        protected String getUnavailabilityReason() {
            return "has support";
        }
    }

    public static class UnsupportedNativeToolChain implements NativeToolChainInternal {
        private final ToolSearchResult failure;

        UnsupportedNativeToolChain(ToolSearchResult failure) {
            this.failure = failure;
        }

        @Override
        public String getDisplayName() {
            return getName();
        }

        @Override
        public String getName() {
            return "unsupported";
        }

        @Override
        public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure);
        }

        @Override
        public PlatformToolProvider select(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
            return select(targetMachine);
        }

        @Override
        public String getOutputType() {
            return "unsupported";
        }

        @Override
        public void assertSupported() {
            TreeFormatter formatter = new TreeFormatter();
            failure.explain(formatter);
            throw new GradleException(formatter.toString());
        }
    }
}
