/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;

import java.io.File;
import java.io.IOException;

public class VisualCppPCHSourceFileGeneratorUtil {
    private static SourceFileExtensionCalculator calculator = new SourceFileExtensionCalculator();

    public static <T extends NativeCompileSpec> File generatePCHSourceFile(T original, File sourceFile) {
        File generatedSourceDir = new File(original.getTempDir(), "pchGeneratedSource");
        generatedSourceDir.mkdirs();
        File generatedSource = new File(generatedSourceDir, FilenameUtils.removeExtension(sourceFile.getName()).concat(calculator.transform(original.getClass())));
        File headerFileCopy = new File(generatedSourceDir, sourceFile.getName());
        try {
            FileUtils.copyFile(sourceFile, headerFileCopy);
            FileUtils.writeStringToFile(generatedSource, "#include \"".concat(headerFileCopy.getName()).concat("\""));
            return generatedSource;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class SourceFileExtensionCalculator implements Transformer<String, Class<? extends NativeCompileSpec>> {
        @Override
        public String transform(Class<? extends NativeCompileSpec> specClass) {
            if (CPCHCompileSpec.class.isAssignableFrom(specClass)) {
                return ".c";
            }

            if (CppPCHCompileSpec.class.isAssignableFrom(specClass)) {
                return ".cpp";
            }

            throw new GradleException("Cannot determine source file extension for spec with type ".concat(specClass.getSimpleName()));
        }
    }
}
