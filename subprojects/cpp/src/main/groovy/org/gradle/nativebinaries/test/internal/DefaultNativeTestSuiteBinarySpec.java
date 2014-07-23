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
package org.gradle.nativebinaries.test.internal;

import org.gradle.nativebinaries.NativeBinarySpec;
import org.gradle.nativebinaries.NativeComponentSpec;
import org.gradle.nativebinaries.internal.AbstractNativeBinarySpec;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;
import org.gradle.runtime.base.internal.BinaryNamingScheme;

import java.io.File;

public class DefaultNativeTestSuiteBinarySpec extends AbstractNativeBinarySpec implements NativeTestSuiteBinarySpecInternal {
    private final NativeBinarySpec testedBinary;
    private File executableFile;

    public DefaultNativeTestSuiteBinarySpec(NativeComponentSpec owner, NativeBinarySpec testedBinary, BinaryNamingScheme namingScheme, NativeDependencyResolver resolver) {
        super(owner, testedBinary.getFlavor(), (ToolChainInternal) testedBinary.getToolChain(), testedBinary.getTargetPlatform(), testedBinary.getBuildType(), namingScheme, resolver);
        this.testedBinary = testedBinary;
    }

    public NativeBinarySpec getTestedBinary() {
        return testedBinary;
    }

    public File getExecutableFile() {
        return executableFile;
    }

    public void setExecutableFile(File executableFile) {
        this.executableFile = executableFile;
    }

    public File getPrimaryOutput() {
        return getExecutableFile();
    }
}
