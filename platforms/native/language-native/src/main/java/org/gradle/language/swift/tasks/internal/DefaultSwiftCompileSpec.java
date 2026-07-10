/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.tasks.internal;

import org.gradle.language.nativeplatform.internal.AbstractNativeCompileSpec;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

import java.io.File;
import java.util.Collection;

public class DefaultSwiftCompileSpec extends AbstractNativeCompileSpec implements SwiftCompileSpec {
    private String moduleName;
    private File moduleFile;
    private SwiftVersion sourceCompatibility;
    private Collection<File> changedFiles;

    @Override
    public String getModuleName() {
        return moduleName;
    }

    @Override
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    @Override
    public File getModuleFile() {
        return moduleFile;
    }

    @Override
    public void setModuleFile(File moduleFile) {
        this.moduleFile = moduleFile;
    }

    @Override
    public SwiftVersion getSourceCompatibility() {
        return sourceCompatibility;
    }

    @Override
    public void setSourceCompatibility(SwiftVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    @Override
    public Collection<File> getChangedFiles() {
        return changedFiles;
    }

    @Override
    public void setChangedFiles(Collection<File> changedFiles) {
        this.changedFiles = changedFiles;
    }
}
