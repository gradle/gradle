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
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SingleSourceCompileArgTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    private final String objectFileNameSuffix;
    private final File sourceFile;
    private final OutputFileArgTransformer outputFileArgTransformer;
    private final boolean windowsPathLengthLimitation;
    private final List<String> invocationArgs;

    public SingleSourceCompileArgTransformer(File sourceFile, String objectFileNameSuffixExtension, List<String> invocationArgs, boolean windowsPathLengthLimitation, OutputFileArgTransformer outputFileArgTransformer) {
        this.sourceFile = sourceFile;
        this.objectFileNameSuffix = objectFileNameSuffixExtension;
        this.outputFileArgTransformer = outputFileArgTransformer;
        this.windowsPathLengthLimitation = windowsPathLengthLimitation;
        this.invocationArgs = invocationArgs;
    }

    public List<String> transform(T spec) {
        List<String> args = new ArrayList<String>(invocationArgs);

        args.add(sourceFile.getAbsolutePath());

        File outputFilePath = getOutputFileDir(sourceFile, spec.getObjectFileDir());
        CollectionUtils.addAll(args, outputFileArgTransformer.transform(outputFilePath));

        return args;
    }

    protected File getOutputFileDir(File sourceFile, File objectFileDir) {
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
