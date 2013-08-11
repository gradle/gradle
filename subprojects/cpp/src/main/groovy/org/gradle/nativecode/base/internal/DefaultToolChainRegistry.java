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
import org.gradle.api.Action;
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

public class DefaultToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<ToolChain> implements ToolChainRegistry {
    private final List<ToolChainInternal> searchOrder = new ArrayList<ToolChainInternal>();
    private boolean hasDefinedToolChain;

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

    public List<ToolChainInternal> getSearchOrder() {
        return searchOrder;
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(ToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends ToolChain> type) {
        if (!hasDefinedToolChain) {
            assertCanAdd(name);
            ToolChain added = doCreate(name, type);
            // Avoid removing defaults
            super.add(added);
        }
    }

    @Override
    public boolean add(ToolChain o) {
        removeDefaults();
        return super.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends ToolChain> c) {
        removeDefaults();
        return super.addAll(c);
    }

    private void removeDefaults() {
        if (!hasDefinedToolChain) {
            clear();
            hasDefinedToolChain = true;
        }
    }

    @Override
    public AbstractNamedDomainObjectContainer<ToolChain> configure(Closure configureClosure) {
        removeDefaults();
        return super.configure(configureClosure);
    }

    public List<ToolChainInternal> getAvailableToolChains() {
        List<ToolChainInternal> availableToolChains = new ArrayList<ToolChainInternal>();
        List<String> messages = new ArrayList<String>();
        for (ToolChainInternal toolChain : searchOrder) {
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

    private static class UnavailableToolChain extends AbstractToolChain {
        private final List<String> messages;

        public UnavailableToolChain(List<String> messages) {
            super("unknown", OperatingSystem.current());
            this.messages = messages;
        }

        @Override
        protected String getTypeName() {
            return "unavailable";
        }

        @Override
        protected void checkAvailable(ToolChainAvailability availability) {
            throw new UnsupportedOperationException();
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
    }
}