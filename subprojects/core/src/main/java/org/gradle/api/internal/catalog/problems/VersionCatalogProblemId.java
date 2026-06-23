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
    INVALID_ALIAS_NOTATION("Invalid alias notation"),
    RESERVED_ALIAS_NAME("Reserved alias name"),
    INVALID_DEPENDENCY_NOTATION("Invalid dependency notation"),
    INVALID_PLUGIN_NOTATION("Invalid plugin notation"),
    INVALID_MODULE_NOTATION("Invalid module notation"),
    INVALID_TOML_DEFINITION("Invalid TOML definition"),
    INVALID_VERSION_NOTATION("Invalid version notation"),
    TOO_MANY_IMPORT_FILES("Importing multiple files is not supported"),
    NO_IMPORT_FILES("No files were resolved to be imported"),
    TOO_MANY_IMPORT_INVOCATION("Multiple 'from' invocations"),
    TOML_SYNTAX_ERROR("TOML syntax error"),
    TOO_MANY_ENTRIES("Too many entries"),
    UNDEFINED_ALIAS_REFERENCE("Bundle declares dependency on non-existent alias"),
    UNDEFINED_VERSION_REFERENCE("Undefined version reference"),
    UNSUPPORTED_FILE_FORMAT("Unsupported file format"),
    UNSUPPORTED_FORMAT_VERSION("Unsupported format version"),
    ALIAS_NOT_FINISHED("Alias builder not finished");

    private final String displayName;

    VersionCatalogProblemId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
