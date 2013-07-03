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

import org.gradle.api.Action;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativecode.base.ToolChain;
import org.gradle.nativecode.base.ToolChainRegistry;

import java.util.ArrayList;
import java.util.List;

public class DefaultToolChainRegistry extends DefaultNamedDomainObjectSet<ToolChain> implements ToolChainRegistry {
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

    public List<ToolChainInternal> getSearchOrder() {
        return searchOrder;
    }

    public ToolChainInternal getDefaultToolChain() {
        List<String> messages = new ArrayList<String>();
        for (ToolChainInternal adapter : searchOrder) {
            ToolChainAvailability availability = adapter.getAvailability();
            if (availability.isAvailable()) {
                return adapter;
            }
            messages.add(String.format("Could not load '%s': %s", adapter.getName(), availability.getUnavailableMessage()));
        }
        return new UnavailableToolChain(messages);
    }

    private static class UnavailableToolChain extends AbstractToolChain {
        private final List<String> messages;

        public UnavailableToolChain(List<String> messages) {
            super(OperatingSystem.current());
            this.messages = messages;
        }

        public String getName() {
            return "unknown";
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

        public <T extends LinkerSpec> Compiler<T> createLinker() {
            throw failure();
        }

        public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
            throw failure();
        }
    }
}