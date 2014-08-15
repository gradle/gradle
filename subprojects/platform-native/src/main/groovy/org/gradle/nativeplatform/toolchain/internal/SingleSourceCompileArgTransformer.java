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

import org.gradle.api.Transformer;
import org.gradle.internal.FileUtils;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SingleSourceCompileArgTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    private final ArgsTransformer<T> delegate;
    private final String objectFileNameSuffix;
    private final File sourceFile;
    private final Transformer<List<String>, File> outputFileArgTransformer;
    private final boolean windowsPathLengthLimitation;

    public SingleSourceCompileArgTransformer(File sourceFile, String objectFileNameSuffixExtension, ArgsTransformer<T> delegate, boolean windowsPathLengthLimitation, Transformer<List<String>, File> outputFileArgTransformer) {
        this.sourceFile = sourceFile;
        this.delegate = delegate;
        this.objectFileNameSuffix = objectFileNameSuffixExtension;
        this.outputFileArgTransformer = outputFileArgTransformer;
        this.windowsPathLengthLimitation = windowsPathLengthLimitation;
    }

    public List<String> transform(T spec) {
        List<String> args = new ArrayList<String>();
        File outputFilePath = getOutputFileDir(sourceFile, spec.getObjectFileDir());

        args.addAll(delegate.transform(spec));
        args.add(sourceFile.getAbsolutePath());

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
