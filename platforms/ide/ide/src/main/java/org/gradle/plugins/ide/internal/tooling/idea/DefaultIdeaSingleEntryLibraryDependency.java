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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.provider.model.internal.LegacyConsumerInterface;

import java.io.File;

@LegacyConsumerInterface("org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency")
public class DefaultIdeaSingleEntryLibraryDependency extends DefaultIdeaDependency {
    private File file;
    private File source;
    private File javadoc;
    private Boolean exported;
    private IdeaDependencyScope scope;
    private GradleModuleVersion moduleVersion;

    public File getFile() {
        return file;
    }

    public DefaultIdeaSingleEntryLibraryDependency setFile(File file) {
        this.file = file;
        return this;
    }

    public File getSource() {
        return source;
    }

    public DefaultIdeaSingleEntryLibraryDependency setSource(File source) {
        this.source = source;
        return this;
    }

    public File getJavadoc() {
        return javadoc;
    }

    public GradleModuleVersion getGradleModuleVersion() {
        return moduleVersion;
    }

    public DefaultIdeaSingleEntryLibraryDependency setJavadoc(File javadoc) {
        this.javadoc = javadoc;
        return this;
    }

    public boolean getExported() {
        return exported;
    }

    public DefaultIdeaSingleEntryLibraryDependency setExported(Boolean exported) {
        this.exported = exported;
        return this;
    }

    public IdeaDependencyScope getScope() {
        return scope;
    }

    public DefaultIdeaSingleEntryLibraryDependency setScope(IdeaDependencyScope scope) {
        this.scope = scope;
        return this;
    }

    public DefaultIdeaSingleEntryLibraryDependency setGradleModuleVersion(GradleModuleVersion moduleVersion) {
        this.moduleVersion = moduleVersion;
        return this;
    }

    @Override
    public String toString() {
        return "IdeaLibraryDependency{"
                + "file=" + file
                + ", source=" + source
                + ", javadoc=" + javadoc
                + ", exported=" + exported
                + ", scope='" + scope + '\''
                + ", id='" + moduleVersion + '\''
                + '}';
    }
}
