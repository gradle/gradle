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

package org.gradle.nativeplatform.toolchain.internal.clang;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.internal.gcc.AbstractGccCompatibleToolChain;
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.CompilerMetaDataProviderFactory;
import org.gradle.process.internal.ExecActionFactory;

public class ClangToolChain extends AbstractGccCompatibleToolChain implements Clang {
    public static final String DEFAULT_NAME = "clang";

    public ClangToolChain(String name, BuildOperationProcessor buildOperationProcessor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, CompilerMetaDataProviderFactory metaDataProviderFactory, Instantiator instantiator) {
        super(name, buildOperationProcessor, operatingSystem, fileResolver, execActionFactory, compilerOutputFileNamingSchemeFactory, metaDataProviderFactory.clang(), instantiator);
    }

    @Override
    protected void configureDefaultTools(DefaultGccPlatformToolChain toolChain) {
        toolChain.getLinker().setExecutable("clang++");
        toolChain.getcCompiler().setExecutable("clang");
        toolChain.getCppCompiler().setExecutable("clang++");
        toolChain.getObjcCompiler().setExecutable("clang");
        toolChain.getObjcppCompiler().setExecutable("clang++");
        toolChain.getAssembler().setExecutable("clang");
    }

    @Override
    protected String getTypeName() {
        return "Clang";
    }

}
