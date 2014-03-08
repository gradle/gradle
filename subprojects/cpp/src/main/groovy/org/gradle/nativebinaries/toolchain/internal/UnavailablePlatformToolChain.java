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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.text.TreeFormatter;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.internal.LinkerSpec;
import org.gradle.nativebinaries.internal.StaticLibraryArchiverSpec;
import org.gradle.util.TreeVisitor;

public class UnavailablePlatformToolChain implements PlatformToolChain {
    private final ToolSearchResult failure;

    public UnavailablePlatformToolChain(ToolSearchResult failure) {
        this.failure = failure;
    }

    public boolean isAvailable() {
        return false;
    }

    public void explain(TreeVisitor<? super String> visitor) {
        failure.explain(visitor);
    }

    private RuntimeException failure() {
        TreeFormatter formatter = new TreeFormatter();
        this.explain(formatter);
        return new GradleException(formatter.toString());
    }

    public <T extends BinaryToolSpec> Compiler<T> createAssembler() {
        throw failure();
    }

    public <T extends BinaryToolSpec> Compiler<T> createCppCompiler() {
        throw failure();
    }

    public <T extends BinaryToolSpec> Compiler<T> createCCompiler() {
        throw failure();
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCppCompiler() {
        throw failure();
    }

    public <T extends BinaryToolSpec> Compiler<T> createObjectiveCCompiler() {
        throw failure();
    }

    public <T extends BinaryToolSpec> Compiler<T> createWindowsResourceCompiler() {
        throw failure();
    }

    public <T extends LinkerSpec> Compiler<T> createLinker() {
        throw failure();
    }

    public <T extends StaticLibraryArchiverSpec> Compiler<T> createStaticLibraryArchiver() {
        throw failure();
    }
}
