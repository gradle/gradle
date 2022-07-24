/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.model.cpp;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.Task;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Represents the compilation details for a binary.
 *
 * @since 4.10
 */
public interface CompilationDetails {
    /**
     * Returns the details of the compilation task for this binary. This is the task that should be run to produce the object files, but may not necessarily be the task that compiles the source files. For example, the task may perform some post processing of the object files.
     */
    Task getCompileTask();

    /**
     * Returns the compiler executable that is used to compile this binary.
     *
     * @return The compiler executable or {@code null} if the compiler for this binary is not available.
     */
    @Nullable
    File getCompilerExecutable();

    /**
     * Returns the working directory that the compiler is invoked from when compiling the source of this binary.
     */
    File getCompileWorkingDir();

    /**
     * Returns the framework search paths for this binary.
     */
    List<File> getFrameworkSearchPaths();

    /**
     * Returns the system search paths for this binary.
     */
    List<File> getSystemHeaderSearchPaths();

    /**
     * Returns the user search paths for this binary. This includes the header directories for the binary itself, plus any dependencies of the binary.
     */
    List<File> getUserHeaderSearchPaths();

    /**
     * Returns the source files for this binary.
     */
    DomainObjectSet<? extends SourceFile> getSources();

    /**
     * Returns the header directories for this binary. These are also included in the result of {@link #getUserHeaderSearchPaths()}.
     */
    Set<File> getHeaderDirs();

    /**
     * Returns the macro define directives for this binary.
     */
    DomainObjectSet<? extends MacroDirective> getMacroDefines();

    /**
     * Returns the macro undefine directives for this binary.
     */
    Set<String> getMacroUndefines();

    /**
     * Returns any additional compiler arguments not included in the search paths and macro directives of this binary.
     */
    List<String> getAdditionalArgs();
}
