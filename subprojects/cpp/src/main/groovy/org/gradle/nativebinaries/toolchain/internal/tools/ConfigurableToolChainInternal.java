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

package org.gradle.nativebinaries.toolchain.internal.tools;

import org.gradle.nativebinaries.toolchain.ConfigurableToolChain;

public interface ConfigurableToolChainInternal extends ConfigurableToolChain{

    /**
     * The C++ compiler.
     */
    GccToolInternal getCCompiler();

    /**
     * The C compiler.
     */
    GccToolInternal getCppCompiler();

    /**
     * The assembler.
     */
    GccToolInternal getAssembler();

    /**
     * The linker.
     */
    GccToolInternal getLinker();

    /**
     * The Objective-C compiler.
     */
    GccToolInternal getObjcCompiler();

    /**
     * The Objective-CPP compiler.
     */
    GccToolInternal getObjcppCompiler();

    /**
     * The static library archiver.
     */
    GccToolInternal getStaticLibArchiver();
}
