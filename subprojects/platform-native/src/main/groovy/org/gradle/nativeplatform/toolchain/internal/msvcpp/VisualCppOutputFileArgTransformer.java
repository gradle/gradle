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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.nativeplatform.toolchain.internal.AbstractOutputFileArgTransformer;
import org.gradle.nativeplatform.toolchain.internal.OutputFileArgTransformer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class VisualCppOutputFileArgTransformer extends AbstractOutputFileArgTransformer {
    VisualCppOutputFileArgTransformer(File sourceFile, File objectFileDir, String objectFileNameSuffix, boolean windowsPathLengthLimitation) {
        super(sourceFile, objectFileDir, objectFileNameSuffix, windowsPathLengthLimitation);
    }

    public List<String> transform(List<String> args) {
        List<String> newArgs = Lists.newArrayList(args);
        File outputFilePath = getOutputFileDir();
        // MSVC doesn't allow a space between Fo and the file name
        newArgs.add("/Fo" + outputFilePath.getAbsolutePath());
        return newArgs;
    }
}