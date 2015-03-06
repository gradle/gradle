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

package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.io.IOException;

public class GccPCHObjectDirectoryGeneratorUtil {
    static <T extends NativeCompileSpec> File generatePCHObjectDirectory(T spec) {
        File generatedDir = new File(spec.getTempDir(), "preCompiledHeaders");
        generatedDir.mkdirs();
        File generatedHeader = new File(generatedDir, spec.getPreCompiledHeaderFile().getName());
        File generatedPCH = new File(generatedDir, spec.getPreCompiledHeaderObjectFile().getName());
        try {
            FileUtils.copyFile(spec.getPreCompiledHeaderFile(), generatedHeader);
            FileUtils.copyFile(spec.getPreCompiledHeaderObjectFile(), generatedPCH);
            return generatedHeader;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
