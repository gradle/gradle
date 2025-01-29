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

package org.gradle.api.internal.tasks.scala;

import org.gradle.language.base.internal.compile.CompilerParameters;

/**
 * Daemon compiler parameters for {@link org.gradle.api.internal.tasks.scala.ZincScalaCompilerFacade}.
 */
public class ScalaCompilerParameters<T extends ScalaJavaJointCompileSpec> extends CompilerParameters {

    private final T compileSpec;

    public ScalaCompilerParameters(String compilerClassName, Object[] compilerInstanceParameters, T compileSpec) {
        super(compilerClassName, compilerInstanceParameters);
        this.compileSpec = compileSpec;
    }

    @Override
    public T getCompileSpec() {
        return compileSpec;
    }

}
