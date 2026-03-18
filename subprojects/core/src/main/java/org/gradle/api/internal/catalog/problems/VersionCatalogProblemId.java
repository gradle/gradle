/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog.problems;

/**
 * Problem IDs for version catalog problems.
 *
 * The lowercase names of these correspond to sections in <a href="https://docs.gradle.org/current/userguide/version_catalog_problems.html">version catalog problems</a>.
 * Always change version_catalog_problems.adoc accordingly when renaming an ID.
 */
public enum VersionCatalogProblemId {

    ACCESSOR_NAME_CLASH("Accessor name clash"),
    CATALOG_FILE_DOES_NOT_EXIST("Import of external catalog file failed"),
    INVALID_ALIAS_NOTATION("The alias notation is invalid"),
    RESERVED_ALIAS_NAME("The alias is reserved"),
    INVALID_DEPENDENCY_NOTATION("The dependency notation is not valid"),
    INVALID_PLUGIN_NOTATION("The plugin notation is not valid"),
    INVALID_MODULE_NOTATION("The module notation is not valid"),
    TOO_MANY_IMPORT_FILES("Importing multiple files is not supported"),
    NO_IMPORT_FILES("No files were resolved to be imported"),
    TOO_MANY_IMPORT_INVOCATION("The 'from' method can only be called once"),
    TOML_SYNTAX_ERROR("TOML syntax error"),
    TOO_MANY_ENTRIES("Too many entries"),
    UNDEFINED_ALIAS_REFERENCE("Bundle declares dependency on non-existent alias"),
    UNDEFINED_VERSION_REFERENCE("The version reference doesn't exist"),
    UNSUPPORTED_FILE_FORMAT("The file format is not supported"),
    UNSUPPORTED_FORMAT_VERSION("The format version is not supported"),
    ALIAS_NOT_FINISHED("Dependency alias builder was not finished");

    private final String displayName;

    VersionCatalogProblemId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
