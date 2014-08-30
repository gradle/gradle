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

public class CompilerMetaDataProviderFactory {
    private final ExecActionFactory factory;

    public CompilerMetaDataProviderFactory(final FileLookup fileLookup) {
        this.factory = new ExecActionFactory() {
            public ExecAction newExecAction() {
                return new DefaultExecAction(fileLookup.getFileResolver());
            }
        };
    }

    public CompilerMetaDataProvider gcc() {
        return GccVersionDeterminer.forGcc(factory);
    }

    public CompilerMetaDataProvider clang() {
        return GccVersionDeterminer.forClang(factory);
    }
}
