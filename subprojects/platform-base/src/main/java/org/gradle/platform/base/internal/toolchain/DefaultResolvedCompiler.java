/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain;

import org.gradle.language.base.internal.compile.*;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.util.TreeVisitor;

public class DefaultResolvedCompiler<C extends CompileSpec> implements ResolvedTool<org.gradle.language.base.internal.compile.Compiler<C>> {
    private final ToolProvider provider;
    private final Class<C> specType;

    public DefaultResolvedCompiler(ToolProvider provider, Class<C> specType) {
        this.provider = provider;
        this.specType = specType;
    }

    @Override
    public Compiler<C> get() {
        return provider.newCompiler(specType);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
    }
}
