/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.idea;

import org.gradle.tooling.model.idea.IdeaLibraryDependency;

import java.io.File;

/**
 * @author: Szczepan Faber, created at: 7/26/11
 */
public class DefaultIdeaLibraryDependency implements IdeaLibraryDependency {

    private File file;
    private File source;
    private File javadoc;
    private Boolean exported;
    private String scope;

    public File getFile() {
        return file;
    }

    public DefaultIdeaLibraryDependency setFile(File file) {
        this.file = file;
        return this;
    }

    public File getSource() {
        return source;
    }

    public DefaultIdeaLibraryDependency setSource(File source) {
        this.source = source;
        return this;
    }

    public File getJavadoc() {
        return javadoc;
    }

    public DefaultIdeaLibraryDependency setJavadoc(File javadoc) {
        this.javadoc = javadoc;
        return this;
    }

    public Boolean getExported() {
        return exported;
    }

    public DefaultIdeaLibraryDependency setExported(Boolean exported) {
        this.exported = exported;
        return this;
    }

    public String getScope() {
        return scope;
    }

    public DefaultIdeaLibraryDependency setScope(String scope) {
        this.scope = scope;
        return this;
    }
}
