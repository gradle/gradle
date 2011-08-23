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
import org.gradle.plugins.binaries.model.Compiler;
import org.gradle.plugins.binaries.model.CompilerRegistry;

public class DefaultCompilerRegistry extends DefaultNamedDomainObjectSet<Compiler> implements CompilerRegistry {

    public DefaultCompilerRegistry(Instantiator instantiator) {
        super(Compiler.class, instantiator);
    }

    public Compiler<?> getDefaultCompiler() {
        // lame impl, unsure how this will work in reality
        return iterator().next();
    }

}