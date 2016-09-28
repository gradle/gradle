/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * Single entry module library
 */
public class SingleEntryModuleLibrary extends ModuleLibrary {

    private ModuleVersionIdentifier moduleVersion;

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in idea format
     * @param javadoc paths to javadoc jars or javadoc folders
     * @param source paths to source jars or source folders
     * @param scope scope
     */
    public SingleEntryModuleLibrary(FilePath library, Set<FilePath> javadoc, Set<FilePath> source, String scope) {
        super(Collections.singletonList(library), javadoc, source, Lists.<JarDirectory>newArrayList(), scope);
    }

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in idea format
     * @param javadoc path to javadoc jars or javadoc folders
     * @param source paths to source jars or source folders
     * @param scope scope
     */
    public SingleEntryModuleLibrary(FilePath library, @Nullable FilePath javadoc, @Nullable FilePath source, String scope) {
        super(
            Collections.singletonList(library),
            javadoc != null ? Collections.singletonList(javadoc) : Lists.<Path>newArrayList(),
            source != null ? Collections.singletonList(source) : Lists.<Path>newArrayList(),
            Sets.<JarDirectory>newLinkedHashSet(),
            scope
        );
    }

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in Path format
     * @param scope scope
     */
    public SingleEntryModuleLibrary(FilePath library, String scope) {
        this(library, Sets.<FilePath>newLinkedHashSet(), Sets.<FilePath>newLinkedHashSet(), scope);
    }

    /**
     * Module version of the library, if any.
     */
    @Nullable
    public ModuleVersionIdentifier getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(@Nullable ModuleVersionIdentifier moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    /**
     * Returns a single jar or class folder
     */
    public File getLibraryFile() {
        return ((FilePath) this.getClasses().iterator().next()).getFile();
    }

    /**
     * Returns a single javadoc jar or javadoc folder
     */
    public File getJavadocFile() {
        if (getJavadoc().size() > 0) {
            return ((FilePath) this.getJavadoc().iterator().next()).getFile();
        } else {
            return null;
        }
    }

    /**
     * Returns a single source jar or source folder
     */
    public File getSourceFile() {
        if (getSources().size() > 0) {
            return ((FilePath) this.getSources().iterator().next()).getFile();
        } else {
            return null;
        }
    }
}
