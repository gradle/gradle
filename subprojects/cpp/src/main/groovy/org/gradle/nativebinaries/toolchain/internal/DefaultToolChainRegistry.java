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
package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.toolchain.ToolChain;
import org.gradle.util.TreeVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<ToolChain> implements ToolChainRegistryInternal {
    private final Map<String, Class<? extends ToolChain>> registeredDefaults = new LinkedHashMap<String, Class<? extends ToolChain>>();
    private final List<ToolChainInternal> searchOrder = new ArrayList<ToolChainInternal>();

    public DefaultToolChainRegistry(Instantiator instantiator) {
        super(ToolChain.class, instantiator);
        whenObjectAdded(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.add((ToolChainInternal) toolChain);
            }
        });
        whenObjectRemoved(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.remove(toolChain);
            }
        });
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(ToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends ToolChain> type) {
        registeredDefaults.put(name, type);
    }

    public void addDefaultToolChains() {
        for (String name : registeredDefaults.keySet()) {
            create(name, registeredDefaults.get(name));
        }
    }

    public ToolChain getForPlatform(Platform targetPlatform) {
        for (ToolChainInternal toolChain : searchOrder) {
            if (toolChain.target(targetPlatform).isAvailable()) {
                return toolChain;
            }
        }

        // No tool chains can build for this platform. Assemble a description of why
        Map<String, PlatformToolChain> candidates = new LinkedHashMap<String, PlatformToolChain>();
        for (ToolChainInternal toolChain : searchOrder) {
            candidates.put(toolChain.getDisplayName(), toolChain.target(targetPlatform));
        }

        return new UnavailableToolChain(new UnavailableToolChainDescription(targetPlatform, candidates));
    }

    private static class UnavailableToolChainDescription implements ToolSearchResult {
        private final Platform targetPlatform;
        private final Map<String, PlatformToolChain> candidates;

        private UnavailableToolChainDescription(Platform targetPlatform, Map<String, PlatformToolChain> candidates) {
            this.targetPlatform = targetPlatform;
            this.candidates = candidates;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(String.format("No tool chain is available to build for platform '%s'", targetPlatform.getName()));
            visitor.startChildren();
            for (Map.Entry<String, PlatformToolChain> entry : candidates.entrySet()) {
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
    private static class UnavailableToolChain implements ToolChainInternal {
        private final OperatingSystem operatingSystem = OperatingSystem.current();
        private final ToolSearchResult failure;

        UnavailableToolChain(ToolSearchResult failure) {
            this.failure = failure;
        }

        public String getDisplayName() {
            return getName();
        }

        public String getName() {
            return "unavailable";
        }

        public PlatformToolChain target(Platform targetPlatform) {
            return new UnavailablePlatformToolChain(failure);
        }

        public String getExecutableName(String executablePath) {
            return operatingSystem.getExecutableName(executablePath);
        }

        public String getSharedLibraryName(String libraryName) {
            return operatingSystem.getSharedLibraryName(libraryName);
        }

        public String getSharedLibraryLinkFileName(String libraryName) {
            return getSharedLibraryName(libraryName);
        }

        public String getStaticLibraryName(String libraryName) {
            return operatingSystem.getStaticLibraryName(libraryName);
        }

        public String getOutputType() {
            return "unavailable";
        }
    }
}