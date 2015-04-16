/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.nativeplatform.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;
import org.gradle.nativeplatform.toolchain.internal.PreCompiledHeader;

import java.io.File;

/**
 * Compiles native source files into object files.
 */
@Incubating
abstract public class AbstractNativeSourceCompileTask extends AbstractNativeCompileTask {
    private String preCompiledHeader;
    private PreCompiledHeader preCompiledHeaderInclude;
    private File prefixHeaderFile;

    public AbstractNativeSourceCompileTask() {
        super();
    }

    @Override
    protected void configureSpec(NativeCompileSpec spec) {
        super.configureSpec(spec);
        if (preCompiledHeaderInclude != null) {
            File pchObjectFile = preCompiledHeaderInclude.getObjectFile();
            File pchDir = PCHUtils.generatePCHObjectDirectory(spec.getTempDir(), getPrefixHeaderFile(), pchObjectFile);
            spec.setPrefixHeaderFile(new File(pchDir, getPrefixHeaderFile().getName()));
            spec.setPreCompiledHeaderObjectFile(new File(pchDir, pchObjectFile.getName()));
            spec.setPreCompiledHeader(DefaultInclude.parse(getPreCompiledHeader(), true).getValue());
        }
    }

    /**
     * Returns the pre-compiled header object file to be used during compilation
     */
    @Nested @Optional
    public PreCompiledHeader getPreCompiledHeaderInclude() {
        return preCompiledHeaderInclude;
    }

    public void preCompiledHeaderInclude(PreCompiledHeader preCompiledHeader) {
        this.preCompiledHeaderInclude = preCompiledHeader;
    }

    /**
     * Get the header string representing the pre-compiled header to be used by the compiler
     */
    String getPreCompiledHeader() {
        return preCompiledHeader;
    }

    public void setPreCompiledHeader(String preCompiledHeader) {
        this.preCompiledHeader = preCompiledHeader;
    }

    /**
     * Returns the pre-compiled header file to be used during compilation
     */
    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }
}
