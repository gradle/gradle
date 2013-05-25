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
package org.gradle.plugins.binaries.model.internal;

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.binaries.model.*;
import org.gradle.plugins.cpp.internal.LinkerSpec;

import java.util.ArrayList;
import java.util.List;

public class DefaultToolChainRegistry extends DefaultNamedDomainObjectSet<ToolChainAdapter> implements ToolChainRegistry {
    private final List<ToolChainAdapter> searchOrder = new ArrayList<ToolChainAdapter>();

    public DefaultToolChainRegistry(Instantiator instantiator) {
        super(ToolChainAdapter.class, instantiator);
        whenObjectAdded(new Action<ToolChainAdapter>() {
            public void execute(ToolChainAdapter binaryCompiler) {
                searchOrder.add(binaryCompiler);
            }
        });
        whenObjectRemoved(new Action<ToolChainAdapter>() {
            public void execute(ToolChainAdapter binaryCompiler) {
                searchOrder.remove(binaryCompiler);
            }
        });
    }

    public List<ToolChainAdapter> getSearchOrder() {
        return searchOrder;
    }

    public ToolChain getDefaultToolChain() {
        return new LazyToolChain() {
            @Override
            protected ToolChainAdapter findAdapter() {
                return findDefaultCompilerAdapter();
            }
        };
    }

    private ToolChainAdapter findDefaultCompilerAdapter() {
        for (ToolChainAdapter adapter : searchOrder) {
            if (adapter.isAvailable()) {
                return adapter;
            }
        }
        throw new IllegalStateException(String.format("No tool chain is available. Searched for %s.", Joiner.on(", ").join(searchOrder)));
    }

    private abstract class LazyToolChain implements ToolChain {
        private ToolChain toolChain;

        public <T extends BinaryCompileSpec> Compiler<T> createCompiler(Class<T> specType) {
            return createLazyCompiler(specType);
        }

        private <T extends BinaryCompileSpec> Compiler<T> createLazyCompiler(final Class<T> specType) {
            return new Compiler<T>() {
                public WorkResult execute(T spec) {
                    return getToolChain().createCompiler(specType).execute(spec);
                }
            };
        }

        public <T extends LinkerSpec> Compiler<T> createLinker(Class<T> specType) {
            return createLazyLinker(specType);
        }

        private <T extends LinkerSpec> Compiler<T> createLazyLinker(final Class<T> specType) {
            return new Compiler<T>() {
                public WorkResult execute(T spec) {
                    return getToolChain().createLinker(specType).execute(spec);
                }
            };
        }

        private ToolChain getToolChain() {
            if (toolChain == null) {
                toolChain = findAdapter().create();
            }
            return toolChain;
        }

        protected abstract ToolChainAdapter findAdapter();
    }
}