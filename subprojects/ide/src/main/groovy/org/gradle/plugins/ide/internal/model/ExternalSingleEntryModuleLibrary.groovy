/*
 * Copyright 2012 the original author or authors.
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



package org.gradle.plugins.ide.internal.model;


import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.plugins.ide.idea.model.FilePath
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary

/**
 * by Szczepan Faber, created at: 4/23/12
 */
class ExternalSingleEntryModuleLibrary extends SingleEntryModuleLibrary {

    private final ModuleVersionIdentifier id

    /**
     * Creates single entry module library
     *
     * @param library a path to jar or class folder in idea format
     * @param javadoc a path to javadoc jar or javadoc folder
     * @param source a path to source jar or source folder
     * @param scope scope
     */
    public ExternalSingleEntryModuleLibrary(FilePath library, FilePath javadoc, FilePath source, String scope,
        ModuleVersionIdentifier id) {
        super(library, javadoc, source, scope);
        this.id = id
    }

    ModuleVersionIdentifier getId () {
        id
    }
}