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

package org.gradle.language.swift.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.language.swift.SwiftBinary;

public class DefaultSwiftBinary implements SwiftBinary {
    private final String name;
    private final Provider<String> module;
    private final FileCollection source;
    private final FileCollection importPath;
    private final FileCollection linkLibs;

    public DefaultSwiftBinary(String name, Provider<String> module, FileCollection source, FileCollection importPath, FileCollection linkLibs) {
        this.name = name;
        this.module = module;
        this.source = source;
        this.importPath = importPath;
        this.linkLibs = linkLibs;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Provider<String> getModule() {
        return module;
    }

    @Override
    public FileCollection getSwiftSource() {
        return source;
    }

    @Override
    public FileCollection getCompileImportPath() {
        return importPath;
    }

    @Override
    public FileCollection getLinkLibraries() {
        return linkLibs;
    }
}
