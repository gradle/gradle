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

package org.gradle.nativeplatform.internal;

import org.apache.commons.io.FilenameUtils;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.hash.HashUtil;

import java.io.File;

public class CompilerOutputFileNamingScheme {
    private String objectFileNameSuffix;
    private File outputBaseFolder;
    private final RelativeFilePathResolver fileResolver;

    public CompilerOutputFileNamingScheme(RelativeFilePathResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public CompilerOutputFileNamingScheme withOutputBaseFolder(File outputBaseFolder) {
        this.outputBaseFolder = outputBaseFolder;
        return this;
    }

    public CompilerOutputFileNamingScheme withObjectFileNameSuffix(String suffix){
        this.objectFileNameSuffix = suffix;
        return this;
    }

    public File map(File sourceFile) {
        final String baseName = FilenameUtils.removeExtension(sourceFile.getName());
        final String uniqueName = generateUniqueNameFor(sourceFile);
        File hashDirectory = new File(outputBaseFolder, uniqueName);
        return new File(hashDirectory, baseName + objectFileNameSuffix);
    }

    protected String generateUniqueNameFor(File sourceFile) {
        return HashUtil.createCompactMD5(fileResolver.resolveAsRelativePath(sourceFile));
    }
}
