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

import org.gradle.internal.FileUtils;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;

import java.io.File;
import java.util.List;

/**
 */
public abstract class AbstractOutputFileArgTransformer implements OutputFileArgTransformer {

    private final File sourceFile;
    private final File objectFileDir;
    private final String objectFileNameSuffix;
    private final boolean windowsPathLengthLimitation;

    public AbstractOutputFileArgTransformer(File sourceFile, File objectFileDir, String objectFileNameSuffix, boolean windowsPathLengthLimitation) {
        this.sourceFile = sourceFile;
        this.objectFileDir = objectFileDir;
        this.objectFileNameSuffix = objectFileNameSuffix;
        this.windowsPathLengthLimitation = windowsPathLengthLimitation;
    }

    protected File getOutputFileDir() {
        File outputFile = new CompilerOutputFileNamingScheme()
                .withObjectFileNameSuffix(objectFileNameSuffix)
                .withOutputBaseFolder(objectFileDir)
                .map(sourceFile);
        File outputDirectory = outputFile.getParentFile();
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        return windowsPathLengthLimitation ? FileUtils.assertInWindowsPathLengthLimitation(outputFile) : outputFile;
    }
}
