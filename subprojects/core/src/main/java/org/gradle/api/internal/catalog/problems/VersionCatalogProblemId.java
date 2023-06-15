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
    ACCESSOR_NAME_CLASH,
    CATALOG_FILE_DOES_NOT_EXIST,
    INVALID_ALIAS_NOTATION,
    INVALID_DEPENDENCY_NOTATION,
    INVALID_PLUGIN_NOTATION,
    INVALID_MODULE_NOTATION,
    TOO_MANY_IMPORT_FILES,
    NO_IMPORT_FILES,
    TOO_MANY_IMPORT_INVOCATION,
    RESERVED_ALIAS_NAME,
    TOML_SYNTAX_ERROR,
    TOO_MANY_ENTRIES,
    UNDEFINED_ALIAS_REFERENCE,
    UNDEFINED_VERSION_REFERENCE,
    UNSUPPORTED_FILE_FORMAT,
    UNSUPPORTED_FORMAT_VERSION,
    ALIAS_NOT_FINISHED,
}
