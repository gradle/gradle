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

package org.gradle.nativebinaries.toolchain.internal;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.internal.hash.HashUtil;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SingleSourceCompileArgTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T> {
    private final ArgsTransformer<T> delegate;
    private final String objectFileName;
    private final File sourceFile;
    private final boolean usingVisualCToolChain;

    public SingleSourceCompileArgTransformer(File sourceFile, String objectFileName, ArgsTransformer<T> delegate, boolean usingVisualCToolChain) {
        this.sourceFile = sourceFile;
        this.delegate = delegate;
        this.objectFileName = objectFileName;
        this.usingVisualCToolChain = usingVisualCToolChain;
    }

    public List<String> transform(T spec) {
        List<String> args = new ArrayList<String>();
        args.addAll(delegate.transform(spec));
        args.add(sourceFile.getAbsolutePath());

        File outputFileDir = getOutputFileDir(sourceFile, spec.getObjectFileDir());
        outputFileDir.mkdir();
        String outputFilePath = new File(outputFileDir, objectFileName).getAbsolutePath();

        if (usingVisualCToolChain) {
            Collections.addAll(args, "/Fo" + outputFilePath);
        } else {
            Collections.addAll(args, "-o", outputFilePath);
        }
        return args;
    }

    protected File getOutputFileDir(File sourceFile, File objectFileDir) {
        String compactMD5 = HashUtil.createCompactMD5(sourceFile.getAbsolutePath());
        return new File(objectFileDir, compactMD5);
    }

}
