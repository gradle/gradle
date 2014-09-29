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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;
import org.gradle.util.TreeVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultNativeToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<NativeToolChain> implements NativeToolChainRegistryInternal {
    private final Map<String, Class<? extends NativeToolChain>> registeredDefaults = new LinkedHashMap<String, Class<? extends NativeToolChain>>();
    private final List<NativeToolChainInternal> searchOrder = new ArrayList<NativeToolChainInternal>();

    public DefaultNativeToolChainRegistry(Instantiator instantiator) {
        super(NativeToolChain.class, instantiator);
        whenObjectAdded(new Action<NativeToolChain>() {
            public void execute(NativeToolChain toolChain) {
                searchOrder.add((NativeToolChainInternal) toolChain);
            }
        });
        whenObjectRemoved(new Action<NativeToolChain>() {
            public void execute(NativeToolChain toolChain) {
                searchOrder.remove(toolChain);
            }
        });
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(NativeToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends NativeToolChain> type) {
        registeredDefaults.put(name, type);
    }

    public void addDefaultToolChains() {
        for (String name : registeredDefaults.keySet()) {
            create(name, registeredDefaults.get(name));
        }
    }

    public NativeToolChain getForPlatform(NativePlatform targetPlatform) {
        for (NativeToolChainInternal toolChain : searchOrder) {
            if (toolChain.select((NativePlatformInternal) targetPlatform).isAvailable()) {
                return toolChain;
            }
        }

        // No tool chains can build for this platform. Assemble a description of why
        Map<String, PlatformToolProvider> candidates = new LinkedHashMap<String, PlatformToolProvider>();
        for (NativeToolChainInternal toolChain : searchOrder) {
            candidates.put(toolChain.getDisplayName(), toolChain.select((NativePlatformInternal) targetPlatform));
        }

        return new UnavailableNativeToolChain(new UnavailableToolChainDescription(targetPlatform, candidates));
    }

    private static class UnavailableToolChainDescription implements ToolSearchResult {
        private final NativePlatform targetPlatform;
        private final Map<String, PlatformToolProvider> candidates;

        private UnavailableToolChainDescription(NativePlatform targetPlatform, Map<String, PlatformToolProvider> candidates) {
            this.targetPlatform = targetPlatform;
            this.candidates = candidates;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(String.format("No tool chain is available to build for platform '%s'", targetPlatform.getName()));
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
    }

    private static class UnavailableNativeToolChain implements NativeToolChainInternal {
        private final ToolSearchResult failure;

        UnavailableNativeToolChain(ToolSearchResult failure) {
            this.failure = failure;
        }

        public String getDisplayName() {
            return getName();
        }

        public String getName() {
            return "unavailable";
        }

        public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure);
        }

        public String getOutputType() {
            return "unavailable";
        }
    }
}