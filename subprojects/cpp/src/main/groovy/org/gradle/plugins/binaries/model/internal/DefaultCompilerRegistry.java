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
import org.gradle.internal.reflect.Instantiator;
import org.gradle.api.tasks.WorkResult;
import org.gradle.plugins.binaries.model.NativeComponent;
import org.gradle.plugins.binaries.model.Compiler;
import org.gradle.plugins.binaries.model.CompilerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultCompilerRegistry extends DefaultNamedDomainObjectSet<Compiler> implements CompilerRegistry, CompileSpecFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompilerRegistry.class);
    private BinaryCompileSpecFactory specFactory;
    private final List<Compiler> searchOrder = new ArrayList<Compiler>();

    public DefaultCompilerRegistry(Instantiator instantiator) {
        super(Compiler.class, instantiator);
        whenObjectAdded(new Action<Compiler>() {
            public void execute(Compiler compiler) {
                searchOrder.add(compiler);
            }
        });
        whenObjectRemoved(new Action<Compiler>() {
            public void execute(Compiler compiler) {
                searchOrder.remove(compiler);
            }
        });
    }

    public List<Compiler> getSearchOrder() {
        return searchOrder;
    }

    public void setSpecFactory(BinaryCompileSpecFactory specFactory) {
        this.specFactory = specFactory;
    }

    public CompilerAdapter<BinaryCompileSpec> getDefaultCompiler() {
        for (Compiler compiler : searchOrder) {
            CompilerAdapter<BinaryCompileSpec> adapter = (CompilerAdapter<BinaryCompileSpec>) compiler;
            if (adapter.isAvailable()) {
                return adapter;
            }
        }
        return null;
    }

    public BinaryCompileSpec create(final NativeComponent binary) {
        org.gradle.api.internal.tasks.compile.Compiler<BinaryCompileSpec> lazyCompiler = new LazyCompiler(binary);
        return specFactory.create(binary, lazyCompiler);
    }

    private class LazyCompiler implements org.gradle.api.internal.tasks.compile.Compiler<BinaryCompileSpec> {
        private final NativeComponent binary;

        public LazyCompiler(NativeComponent binary) {
            this.binary = binary;
        }

        public WorkResult execute(BinaryCompileSpec spec) {
            CompilerAdapter<BinaryCompileSpec> compiler = getDefaultCompiler();
            if (compiler == null) {
                throw new IllegalStateException(String.format("No compiler is available to compile %s. Searched for %s.", binary, Joiner.on(", ").join(searchOrder)));
            }
            LOGGER.info("Using " + compiler + " to compile " + binary);
            return compiler.createCompiler(binary).execute(spec);
        }
    }
}