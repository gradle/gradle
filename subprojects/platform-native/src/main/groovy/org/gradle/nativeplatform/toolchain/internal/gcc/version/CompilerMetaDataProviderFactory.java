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

package org.gradle.nativeplatform.toolchain.internal.gcc.version;

import org.gradle.api.internal.file.FileLookup;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CompilerMetaDataProviderFactory {
    private final CachingCompilerMetaDataProvider gcc;
    private final CachingCompilerMetaDataProvider clang;

    public CompilerMetaDataProviderFactory(final FileLookup fileLookup) {
        ExecActionFactory factory = new ExecActionFactory() {
            public ExecAction newExecAction() {
                return new DefaultExecAction(fileLookup.getFileResolver());
            }
        };
        gcc = new CachingCompilerMetaDataProvider(GccVersionDeterminer.forGcc(factory));
        clang = new CachingCompilerMetaDataProvider(GccVersionDeterminer.forClang(factory));
    }

    public CompilerMetaDataProvider gcc() {
        return gcc;
    }

    public CompilerMetaDataProvider clang() {
        return clang;
    }

    private static class CachingCompilerMetaDataProvider implements CompilerMetaDataProvider {
        private final CompilerMetaDataProvider delegate;
        private final Map<File, GccVersionResult> resultMap = new HashMap<File, GccVersionResult>();

        private CachingCompilerMetaDataProvider(CompilerMetaDataProvider delegate) {
            this.delegate = delegate;
        }

        public GccVersionResult getGccMetaData(File gccBinary) {
            GccVersionResult result = resultMap.get(gccBinary);
            if (result == null) {
                result = delegate.getGccMetaData(gccBinary);
                resultMap.put(gccBinary, result);
            }
            return result;
        }
    }
}
