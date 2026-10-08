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

import org.gradle.api.problems.ProblemGroups;
import org.gradle.api.problems.ProblemId;

/**
 * Problem IDs for version catalog problems.
 *
 * The lowercase names of these correspond to sections in <a href="https://docs.gradle.org/current/userguide/version_catalog_problems.html">version catalog problems</a>.
 * Always change version_catalog_problems.adoc accordingly when renaming an ID.
 * <p>
 * The problems are reported in {@code Dependencies > Declaration}.
 */
public enum VersionCatalogProblemId {

    ACCESSOR_NAME_CLASH("Version catalog accessor name clash"),
    CATALOG_FILE_DOES_NOT_EXIST("Import of external version catalog file failed"),
    INVALID_ALIAS_NOTATION("Invalid version catalog alias notation"),
    RESERVED_ALIAS_NAME("Reserved version catalog alias name"),
    INVALID_DEPENDENCY_NOTATION("Invalid version catalog dependency notation"),
    INVALID_PLUGIN_NOTATION("Invalid version catalog plugin notation"),
    INVALID_MODULE_NOTATION("Invalid version catalog module notation"),
    INVALID_TOML_DEFINITION("Invalid version catalog TOML definition"),
    INVALID_VERSION_NOTATION("Invalid version notation in version catalog"),
    TOO_MANY_IMPORT_FILES("Importing multiple version catalog files is not supported"),
    NO_IMPORT_FILES("No version catalog files were resolved to be imported"),
    TOO_MANY_IMPORT_INVOCATION("Multiple 'from' invocations in version catalog"),
    TOML_SYNTAX_ERROR("Version catalog TOML syntax error"),
    TOO_MANY_ENTRIES("Too many entries in version catalog"),
    UNDEFINED_ALIAS_REFERENCE("Version catalog bundle declares dependency on non-existent alias"),
    UNDEFINED_VERSION_REFERENCE("Undefined version reference in version catalog"),
    UNSUPPORTED_FILE_FORMAT("Unsupported version catalog file format"),
    UNSUPPORTED_FORMAT_VERSION("Unsupported version catalog format version"),
    ALIAS_NOT_FINISHED("Version catalog alias builder not finished");

    private final String name;

    VersionCatalogProblemId(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ProblemId problemId(ProblemGroups groups) {
        return groups.getDependencies().getDeclaration().problemId(name);
    }
}
