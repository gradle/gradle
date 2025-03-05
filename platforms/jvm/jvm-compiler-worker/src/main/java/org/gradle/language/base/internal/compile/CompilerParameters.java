/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.language.base.internal.compile;

import org.gradle.workers.WorkParameters;

import java.io.Serializable;

/**
 * Parameters which are serialized to compiler daemons and used by {@link CompilerWorkAction}s.
 */
public abstract class CompilerParameters implements WorkParameters, Serializable {

    private final String compilerClassName;
    private final Object[] compilerInstanceParameters;

    public CompilerParameters(String compilerClassName, Object[] compilerInstanceParameters) {
        this.compilerClassName = compilerClassName;
        this.compilerInstanceParameters = compilerInstanceParameters;
    }

    public String getCompilerClassName() {
        return compilerClassName;
    }

    public Object[] getCompilerInstanceParameters() {
        return compilerInstanceParameters;
    }

    abstract public Object getCompileSpec();

}
