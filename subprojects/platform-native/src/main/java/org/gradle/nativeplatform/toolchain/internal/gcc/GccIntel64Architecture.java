/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;

import java.util.List;

/**
 * Default TargetPlatformConfiguration for GCC x86_64 targets
 */
public class GccIntel64Architecture implements TargetPlatformConfiguration {
    @Override
    public boolean supportsPlatform(NativePlatformInternal targetPlatform) {
        return targetPlatform.getOperatingSystem().isCurrent()
            && targetPlatform.getArchitecture().isAmd64();
    }

    @Override
    public void apply(DefaultGccPlatformToolChain gccToolChain) {
        gccToolChain.compilerProbeArgs("-m64");
        Action<List<String>> m64args = new Action<List<String>>() {
            public void execute(List<String> args) {
                args.add("-m64");
            }
        };
        gccToolChain.getCppCompiler().withArguments(m64args);
        gccToolChain.getcCompiler().withArguments(m64args);
        gccToolChain.getObjcCompiler().withArguments(m64args);
        gccToolChain.getObjcppCompiler().withArguments(m64args);
        gccToolChain.getLinker().withArguments(m64args);
        gccToolChain.getAssembler().withArguments(m64args);
    }
}
