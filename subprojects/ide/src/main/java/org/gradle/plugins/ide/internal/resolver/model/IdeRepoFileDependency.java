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

package org.gradle.plugins.ide.internal.resolver.model;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.io.File;

public class IdeRepoFileDependency extends IdeDependency {
    private final File file;
    private File sourceFile;
    private File javadocFile;
    private ModuleVersionIdentifier id;

    public IdeRepoFileDependency(Configuration declaredConfiguration, File file) {
        super(declaredConfiguration);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public File getJavadocFile() {
        return javadocFile;
    }

    public void setJavadocFile(File javadocFile) {
        this.javadocFile = javadocFile;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public void setId(ModuleVersionIdentifier id) {
        this.id = id;
    }
}