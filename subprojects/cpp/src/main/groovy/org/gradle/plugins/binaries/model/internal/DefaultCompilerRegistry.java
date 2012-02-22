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

import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.tasks.WorkResult;
import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.Compiler;
import org.gradle.plugins.binaries.model.CompilerRegistry;

public class DefaultCompilerRegistry extends DefaultNamedDomainObjectSet<Compiler> implements CompilerRegistry, CompileSpecFactory {
    private BinaryCompileSpecFactory specFactory;

    public DefaultCompilerRegistry(Instantiator instantiator) {
        super(Compiler.class, instantiator);
    }

    public void setSpecFactory(BinaryCompileSpecFactory specFactory) {
        this.specFactory = specFactory;
    }

    public CompilerAdapter<BinaryCompileSpec> getDefaultCompiler() {
        for (Compiler compiler : this) {
            CompilerAdapter<BinaryCompileSpec> adapter = (CompilerAdapter<BinaryCompileSpec>) compiler;
            if (adapter.isAvailable()) {
                return adapter;
            }
        }
        throw new IllegalStateException("No compiler is available.");
    }

    public BinaryCompileSpec create(final Binary binary) {
        org.gradle.api.internal.tasks.compile.Compiler<BinaryCompileSpec> lazyCompiler = new org.gradle.api.internal.tasks.compile.Compiler<BinaryCompileSpec>() {
            public WorkResult execute(BinaryCompileSpec spec) {
                return getDefaultCompiler().createCompiler(binary).execute(spec);
            }
        };
        return specFactory.create(binary, lazyCompiler);
    }
}