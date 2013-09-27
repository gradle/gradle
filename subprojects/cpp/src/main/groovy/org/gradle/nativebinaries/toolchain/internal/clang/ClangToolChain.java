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

package org.gradle.nativebinaries.toolchain.internal.clang;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.PlatformToolChain;
import org.gradle.nativebinaries.internal.ToolChainAvailability;
import org.gradle.nativebinaries.toolchain.Clang;
import org.gradle.nativebinaries.toolchain.internal.AbstractToolChain;
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolType;
import org.gradle.nativebinaries.toolchain.internal.gcc.GnuCompatibleToolChain;
import org.gradle.process.internal.ExecActionFactory;

public class ClangToolChain extends AbstractToolChain implements Clang {
    public static final String DEFAULT_NAME = "clang";
    private final ExecActionFactory execActionFactory;

    public ClangToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory) {
        super(name, operatingSystem, new ToolRegistry(operatingSystem), fileResolver);
        this.execActionFactory = execActionFactory;

        tools.setExeName(ToolType.CPP_COMPILER, "clang++");
        tools.setExeName(ToolType.C_COMPILER, "clang");
        tools.setExeName(ToolType.ASSEMBLER, "as");
        tools.setExeName(ToolType.LINKER, "clang++");
        tools.setExeName(ToolType.STATIC_LIB_ARCHIVER, "ar");
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        for (ToolType key : ToolType.values()) {
            availability.mustExist(key.getToolName(), tools.locate(key));
        }
    }

    @Override
    protected String getTypeName() {
        return "Clang";
    }

    public PlatformToolChain target(Platform targetPlatform) {
        checkAvailable();
        return new GnuCompatibleToolChain(tools, operatingSystem, execActionFactory, targetPlatform, true);
    }
}
