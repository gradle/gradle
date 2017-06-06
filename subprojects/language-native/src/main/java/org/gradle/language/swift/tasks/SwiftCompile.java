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

package org.gradle.language.swift.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.language.nativeplatform.tasks.AbstractNativeSourceCompileTask;
import org.gradle.language.swift.internal.DefaultSwiftCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.SwiftCompileSpec;

import java.io.File;

public class SwiftCompile extends AbstractNativeSourceCompileTask {
    private File outputFile;
    private String moduleName;
    private final ConfigurableFileCollection libs;

    public SwiftCompile() {
        libs = getProject().files();
    }

    @Override
    protected NativeCompileSpec createCompileSpec() {
        SwiftCompileSpec spec = new DefaultSwiftCompileSpec();
        spec.setOutputFile(outputFile);
        spec.setModuleName(moduleName);
        spec.libraries(getLibs());
        return spec;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Optional
    @Input
    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    /**
     * The library files to be passed to the linker.
     */
    @InputFiles
    public FileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs.setFrom(libs);
    }

    /**
     * Adds a set of library files to be linked. The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void lib(Object libs) {
        this.libs.from(libs);
    }
}
