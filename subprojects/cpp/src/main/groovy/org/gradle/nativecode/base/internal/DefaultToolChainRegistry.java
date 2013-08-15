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
package org.gradle.nativecode.base.internal;

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativecode.base.ToolChain;
import org.gradle.nativecode.base.ToolChainRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// TODO:DAZ Need a better way of having a container with defaults. Similar for FlavorContainer.
// Probably need a separate container for defaults and registered, and then have a simple delegating container, or factory.
public class DefaultToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<ToolChain> implements ToolChainRegistry {
    private ToolChain defaultToolChain;

    public DefaultToolChainRegistry(Instantiator instantiator) {
        super(ToolChain.class, instantiator);
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(ToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends ToolChain> type) {
        if (isEmpty()) {
            assertCanAdd(name);
            ToolChain added = doCreate(name, type);
            ToolChainInternal candidate = (ToolChainInternal) added;
            if (candidate.getAvailability().isAvailable()) {
                add(candidate);
            }
            defaultToolChain = candidate;
        }
    }

    @Override
    public boolean add(ToolChain o) {
        removeDefault();
        return super.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends ToolChain> c) {
        removeDefault();
        return super.addAll(c);
    }

    @Override
    public AbstractNamedDomainObjectContainer<ToolChain> configure(Closure configureClosure) {
        removeDefault();
        return super.configure(configureClosure);
    }

    private void removeDefault() {
        if (defaultToolChain != null) {
            remove(defaultToolChain);
            defaultToolChain = null;
        }
    }

    public List<ToolChainInternal> getAvailableToolChains() {
        List<ToolChainInternal> availableToolChains = new ArrayList<ToolChainInternal>();
        List<String> messages = new ArrayList<String>();
        for (ToolChainInternal toolChain : this.withType(ToolChainInternal.class)) {
            ToolChainAvailability availability = toolChain.getAvailability();
            if (availability.isAvailable()) {
                availableToolChains.add(toolChain);
            }
            messages.add(String.format("Could not load '%s': %s", toolChain.getName(), availability.getUnavailableMessage()));
        }
        if (availableToolChains.isEmpty()) {
            availableToolChains.add(new UnavailableToolChain(messages));
        }
        return availableToolChains;
    }

    public ToolChainInternal getDefaultToolChain() {
        return getAvailableToolChains().get(0);
    }

    private static class UnavailableToolChain implements ToolChainInternal {
        private final List<String> messages;
        private final OperatingSystem operatingSystem = OperatingSystem.current();

        public UnavailableToolChain(List<String> messages) {
            this.messages = messages;
        }

        public String getName() {
            return "unavailable";
        }

        private IllegalStateException failure() {
            return new IllegalStateException(String.format("No tool chain is available: %s", messages));
        }

        public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
            throw failure();
        }

        public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
            throw failure();
        }

        public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
            throw failure();
        }

        public <T extends LinkerSpec> Compiler<T> createLinker() {
            throw failure();
        }

        public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
            throw failure();
        }

        public ToolChainAvailability getAvailability() {
            return new ToolChainAvailability().unavailable("No tool chain is available.");
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
            throw failure();
        }

    }
}