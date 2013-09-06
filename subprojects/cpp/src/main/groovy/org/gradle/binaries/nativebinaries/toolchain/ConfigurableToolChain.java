/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.binaries.nativebinaries.toolchain;

import org.gradle.api.Incubating;
import org.gradle.binaries.nativebinaries.ToolChain;

import java.io.File;
import java.util.List;

/**
 * A tool chain that allows the individual tools to be configured.
 */
@Incubating
public interface ConfigurableToolChain extends ToolChain {
    /**
     * The paths setting required for executing the tool chain.
     */
    List<File> getPaths();
    // TODO:DAZ Add a setter

    /**
     * Add an entry or entries to the tool chain path.
     */
    void path(Object... pathEntry);

    /**
     * The C++ compiler.
     */
    Tool getCCompiler();

    /**
     * The C compiler.
     */
    Tool getCppCompiler();

    /**
     * The assembler.
     */
    Tool getAssembler();

    /**
     * The linker.
     */
    Tool getLinker();

    /**
     * The static library archiver.
     */
    Tool getStaticLibArchiver();
}
