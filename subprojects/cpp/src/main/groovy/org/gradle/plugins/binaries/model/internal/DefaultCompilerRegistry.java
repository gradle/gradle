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
import org.gradle.plugins.binaries.model.BinaryCompiler;
import org.gradle.plugins.binaries.model.CompilerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultCompilerRegistry extends DefaultNamedDomainObjectSet<BinaryCompiler> implements CompilerRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompilerRegistry.class);
    private final List<BinaryCompiler> searchOrder = new ArrayList<BinaryCompiler>();

    public DefaultCompilerRegistry(Instantiator instantiator) {
        super(BinaryCompiler.class, instantiator);
        whenObjectAdded(new Action<BinaryCompiler>() {
            public void execute(BinaryCompiler binaryCompiler) {
                searchOrder.add(binaryCompiler);
            }
        });
        whenObjectRemoved(new Action<BinaryCompiler>() {
            public void execute(BinaryCompiler binaryCompiler) {
                searchOrder.remove(binaryCompiler);
            }
        });
    }

    public List<BinaryCompiler> getSearchOrder() {
        return searchOrder;
    }

    public Compiler<BinaryCompileSpec> getDefaultCompiler() {
        return new LazyCompiler();
    }

    private CompilerAdapter<BinaryCompileSpec> findDefaultCompilerAdapter() {
        for (BinaryCompiler binaryCompiler : searchOrder) {
            CompilerAdapter<BinaryCompileSpec> adapter = (CompilerAdapter<BinaryCompileSpec>) binaryCompiler;
            if (adapter.isAvailable()) {
                return adapter;
            }
        }
        return null;
    }

    private class LazyCompiler implements org.gradle.api.internal.tasks.compile.Compiler<BinaryCompileSpec> {
        public WorkResult execute(BinaryCompileSpec spec) {
            CompilerAdapter<BinaryCompileSpec> compiler = findDefaultCompilerAdapter();
            if (compiler == null) {
                throw new IllegalStateException(String.format("No compiler is available. Searched for %s.", Joiner.on(", ").join(searchOrder)));
            }
            LOGGER.info("Using " + compiler);
            return compiler.createCompiler().execute(spec);
        }
    }
}