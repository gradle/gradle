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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PCHUtils {
    public static File generatePCHObjectDirectory(File tempDir, File prefixHeaderFile, File preCompiledHeaderObjectFile) {
        File generatedDir = new File(tempDir, "preCompiledHeaders");
        generatedDir.mkdirs();
        File generatedHeader = new File(generatedDir, prefixHeaderFile.getName());
        File generatedPCH = new File(generatedDir, preCompiledHeaderObjectFile.getName());
        try {
            FileUtils.copyFile(prefixHeaderFile, generatedHeader);
            FileUtils.copyFile(preCompiledHeaderObjectFile, generatedPCH);
            return generatedDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void generatePrefixHeaderFile(List<String> headers, File headerFile) {
        if (!headerFile.getParentFile().exists()) {
            headerFile.getParentFile().mkdirs();
        }

        try {
            FileUtils.writeLines(headerFile, CollectionUtils.collect(headers, new Transformer<String, String>() {
                @Override
                public String transform(String header) {
                    if (header.startsWith("<")) {
                        return "#include ".concat(header);
                    } else {
                        return "#include \"".concat(header).concat("\"");
                    }
                }
            }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T extends NativeCompileSpec> File generatePCHSourceFile(T original, File sourceFile) {
        File generatedSourceDir = new File(original.getTempDir(), "pchGenerated");
        generatedSourceDir.mkdirs();
        File generatedSource = new File(generatedSourceDir, FilenameUtils.removeExtension(sourceFile.getName()).concat(getSourceFileExtension(original.getClass())));
        File headerFileCopy = new File(generatedSourceDir, sourceFile.getName());
        try {
            FileUtils.copyFile(sourceFile, headerFileCopy);
            FileUtils.writeStringToFile(generatedSource, "#include \"".concat(headerFileCopy.getName()).concat("\""), StandardCharsets.UTF_8);
            return generatedSource;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T extends NativeCompileSpec> Transformer<T, T> getHeaderToSourceFileTransformer(Class<T> type) {
        return new Transformer<T, T>() {
            @Override
            public T transform(T original) {
                List<File> newSourceFiles = Lists.newArrayList();
                for (File sourceFile : original.getSourceFiles()) {
                    newSourceFiles.add(generatePCHSourceFile(original, sourceFile));
                }
                original.setSourceFiles(newSourceFiles);
                return original;
            }
        };
    }

    private static String getSourceFileExtension(Class<? extends NativeCompileSpec> specClass) {
        if (CPCHCompileSpec.class.isAssignableFrom(specClass)) {
            return ".c";
        }

        if (CppPCHCompileSpec.class.isAssignableFrom(specClass)) {
            return ".cpp";
        }

        throw new IllegalArgumentException("Cannot determine source file extension for spec with type ".concat(specClass.getSimpleName()));

    }
}
