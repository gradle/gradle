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
package org.gradle.nativeplatform.test.internal;

import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.internal.AbstractNativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;

import java.io.File;

public class DefaultNativeTestSuiteBinarySpec extends AbstractNativeBinarySpec implements NativeTestSuiteBinarySpecInternal {
    private NativeBinarySpec testedBinary;
    private File executableFile;

    public NativeBinarySpec getTestedBinary() {
        return testedBinary;
    }

    public void setTestedBinary(NativeBinarySpecInternal testedBinary) {
        this.testedBinary = testedBinary;
        setTargetPlatform(testedBinary.getTargetPlatform());
        setToolChain(testedBinary.getToolChain());
        setPlatformToolProvider(testedBinary.getPlatformToolProvider());
        setBuildType(testedBinary.getBuildType());
        setFlavor(testedBinary.getFlavor());
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
