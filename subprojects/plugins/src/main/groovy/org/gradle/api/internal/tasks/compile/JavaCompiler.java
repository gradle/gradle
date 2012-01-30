/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.compile.CompileOptions;

import java.io.File;

public interface JavaCompiler extends JvmCompiler {
    CompileOptions getCompileOptions();

    void setCompileOptions(CompileOptions compileOptions);

    void setSourceCompatibility(String sourceCompatibility);

    void setTargetCompatibility(String targetCompatibility);

    void setDependencyCacheDir(File dependencyCacheDir);

    /**
     * Configures another compiler with all settings of this compiler.
     * Useful when compilation needs to be delegated to another compiler.
     * This method could be pushed up in the hierarchy if needed.
     */
    void configure(JavaCompiler other);
}
