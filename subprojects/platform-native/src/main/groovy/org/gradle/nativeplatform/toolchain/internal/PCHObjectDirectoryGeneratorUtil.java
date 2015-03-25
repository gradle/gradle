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

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;

public class PCHObjectDirectoryGeneratorUtil {
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
}
