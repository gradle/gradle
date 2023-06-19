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
package org.gradle.api.internal.catalog.parser;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interners;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.apache.commons.lang.StringUtils.uncapitalize;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.createVersionCatalogError;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.getInVersionCatalog;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.getProblemInVersionCatalog;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.throwErrorWithNewProblemsApi;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_MODULE_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_PLUGIN_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOML_SYNTAX_ERROR;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNSUPPORTED_FORMAT_VERSION;
import static org.gradle.problems.internal.RenderingUtils.oxfordListOf;
import static org.gradle.util.internal.TextUtil.getPluralEnding;

public class TomlCatalogFileParser {
    public static final String CURRENT_VERSION = "1.1";
    private static final Splitter SPLITTER = Splitter.on(":").trimResults();
    private static final String METADATA_KEY = "metadata";
    private static final String LIBRARIES_KEY = "libraries";
    private static final String BUNDLES_KEY = "bundles";
    private static final String VERSIONS_KEY = "versions";
    private static final String PLUGINS_KEY = "plugins";
    private static final Set<String> TOP_LEVEL_ELEMENTS = ImmutableSet.of(
        METADATA_KEY,
        LIBRARIES_KEY,
        BUNDLES_KEY,
        VERSIONS_KEY,
        PLUGINS_KEY
    );
    private static final Set<String> PLUGIN_COORDINATES = ImmutableSet.of(
        "id",
        "version"
    );
    private static final Set<String> LIBRARY_COORDINATES = ImmutableSet.of(
        "group",
        "name",
        "version",
        "module"
    );
    private static final Set<String> VERSION_KEYS = ImmutableSet.of(
        "ref",
        "require",
        "strictly",
        "prefer",
        "reject",
        "rejectAll"
    );

    public static void parse(Path catalogFilePath, VersionCatalogBuilder builder) throws IOException {
        StrictVersionParser strictVersionParser = new StrictVersionParser(Interners.newStrongInterner());
        try (InputStream inputStream = Files.newInputStream(catalogFilePath)) {
            TomlParseResult result = Toml.parse(inputStream);
            assertNoParseErrors(result, catalogFilePath, builder);
            TomlTable metadataTable = result.getTable(METADATA_KEY);
            verifyMetadata(builder, metadataTable);
            TomlTable librariesTable = result.getTable(LIBRARIES_KEY);
            TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
            TomlTable versionsTable = result.getTable(VERSIONS_KEY);
            TomlTable pluginsTable = result.getTable(PLUGINS_KEY);
            Sets.SetView<String> unknownTle = Sets.difference(result.keySet(), TOP_LEVEL_ELEMENTS);
            if (!unknownTle.isEmpty()) {
                throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog(builder) + ", unknown top level elements " + unknownTle, TOML_SYNTAX_ERROR)
                    .description("TOML file contains an unexpected top-level element")
                    .solution("Make sure the top-level elements of your TOML file is one of " + oxfordListOf(TOP_LEVEL_ELEMENTS, "or")));
            }
            parseLibraries(librariesTable, builder, strictVersionParser);
            parsePlugins(pluginsTable, builder, strictVersionParser);
            parseBundles(bundlesTable, builder);
            parseVersions(versionsTable, builder, strictVersionParser);
        }
    }

    private static void assertNoParseErrors(TomlParseResult result, Path catalogFilePath, VersionCatalogBuilder builder) {
        if (result.hasErrors()) {
            List<TomlParseError> errors = result.errors();
            throw throwVersionCatalogProblemException(
                createVersionCatalogError(getProblemInVersionCatalog(builder) + ", parsing failed with " + errors.size() + " error" + getPluralEnding(errors) + ".", TOML_SYNTAX_ERROR)
                    .description(getErrorText(catalogFilePath, errors))
                    .solution("Fix the TOML file according to the syntax described at https://toml.io"));
        }
    }

    private static String getErrorText(Path catalogFilePath, List<TomlParseError> errors) {
        return errors.stream().map(error -> "In file '" +
                catalogFilePath.toAbsolutePath() +
                "' at line " +
                error.position().line() + ", column " +
                error.position().column() +
                ": " +
                error.getMessage())
            .collect(joining("\n"));
    }

    private static void verifyMetadata(VersionCatalogBuilder builder, @Nullable TomlTable metadataTable) {
        if (metadataTable != null) {
            String format = metadataTable.getString("format.version");
            if (format != null && !CURRENT_VERSION.equals(format)) {
                throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog(builder) + ", unsupported version catalog format " + format + ".", UNSUPPORTED_FORMAT_VERSION)
                    .description("This version of Gradle only supports format version " + CURRENT_VERSION)
                    .solution("Try to upgrade to a newer version of Gradle which supports the catalog format version " + format + "."));
            }
        }
    }

    private static void parseLibraries(@Nullable TomlTable librariesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        if (librariesTable == null) {
            return;
        }
        librariesTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parseLibrary(alias, librariesTable, builder, strictVersionParser));
    }

    private static void parsePlugins(@Nullable TomlTable pluginsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        if (pluginsTable == null) {
            return;
        }
        pluginsTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parsePlugin(alias, pluginsTable, builder, strictVersionParser));
    }

    private static void parseVersions(@Nullable TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        if (versionsTable == null) {
            return;
        }
        versionsTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parseVersion(alias, versionsTable, builder, strictVersionParser));
    }

    private static void parseBundles(@Nullable TomlTable bundlesTable, VersionCatalogBuilder builder) {
        if (bundlesTable == null) {
            return;
        }
        bundlesTable.keySet()
            .stream()
            .sorted()
            .forEach(alias -> {
                List<String> bundled = expectArray(builder, "bundle", alias, bundlesTable, alias).toList().stream()
                    .map(String::valueOf)
                    .collect(toList());
                builder.bundle(alias, bundled);
            });
    }

    @Nullable
    private static String expectString(VersionCatalogBuilder builder, String kind, String name, TomlTable table, @Nullable String element) {
        try {
            String path = name;
            if (element != null) {
                path += "." + element;
            }
            return notEmpty(builder, table.getString(path), element, name);
        } catch (TomlInvalidTypeException ex) {
            return throwUnexpectedTypeError(builder, kind, name, "a string", ex);
        }
    }

    private static <T> T throwUnexpectedTypeError(VersionCatalogBuilder builder, String kind, String name, String typeLabel, TomlInvalidTypeException ex) {
        throw throwVersionCatalogProblemException(createVersionCatalogError("Unexpected type for " + kind + " '" + name + "'", TOML_SYNTAX_ERROR)
            .description("Expected " + typeLabel + " but " + uncapitalize(ex.getMessage()))
            .solution("Use " + typeLabel + " instead"));
    }

    @Nullable
    private static TomlArray expectArray(VersionCatalogBuilder builder, String kind, String alias, TomlTable table, String element) {
        try {
            return table.getArray(element);
        } catch (TomlInvalidTypeException ex) {
            return throwUnexpectedTypeError(builder, kind, alias, "an array", ex);
        }
    }

    @Nullable
    private static Boolean expectBoolean(VersionCatalogBuilder builder, String kind, String alias, TomlTable table, String element) {
        try {
            return table.getBoolean(element);
        } catch (TomlInvalidTypeException ex) {
            return throwUnexpectedTypeError(builder, kind, alias, "a boolean", ex);
        }
    }

    private static void expectedKeys(TomlTable table, Set<String> allowedKeys, String context) {
        Set<String> actualKeys = table.keySet();
        if (!allowedKeys.containsAll(actualKeys)) {
            Set<String> difference = Sets.difference(actualKeys, allowedKeys);
            throw new InvalidUserDataException("On " + context + " expected to find any of " + oxfordListOf(allowedKeys, "or")
                + " but found unexpected key" + getPluralEnding(difference) + " " + oxfordListOf(difference, "and")
                + ".");
        }
    }

    private static void parseLibrary(String alias, TomlTable librariesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        Object gav = librariesTable.get(alias);
        if (gav instanceof String) {
            List<String> split = SPLITTER.splitToList((String) gav);
            if (split.size() == 3) {
                String group = notEmpty(builder, split.get(0), "group", alias);
                String name = notEmpty(builder, split.get(1), "name", alias);
                String version = notEmpty(builder, split.get(2), "version", alias);
                StrictVersionParser.RichVersion rich = strictVersionParser.parse(version);
                registerDependency(builder, alias, group, name, null, rich.require, rich.strictly, rich.prefer, null, null);
                return;
            } else {
                throw throwVersionCatalogProblemException(createVersionCatalogError(getInVersionCatalog(builder.getName()) + ", on alias '" + alias + "' notation '" + gav + "' is not a valid dependency notation.", INVALID_DEPENDENCY_NOTATION)
                    .description("When using a string to declare library coordinates, you must use a valid dependency notation")
                    .solution("Make sure that the coordinates consist of 3 parts separated by colons, eg: my.group:artifact:1.2"));
            }
        }
        if (gav instanceof TomlTable) {
            expectedKeys((TomlTable) gav, LIBRARY_COORDINATES, "library declaration '" + alias + "'");
        }
        String group = expectString(builder, "alias", alias, librariesTable, "group");
        String name = expectString(builder, "alias", alias, librariesTable, "name");
        Object version = librariesTable.get(alias + ".version");
        String mi = expectString(builder, "alias", alias, librariesTable, "module");
        if (mi != null) {
            List<String> split = SPLITTER.splitToList(mi);
            if (split.size() == 2) {
                group = notEmpty(builder, split.get(0), "group", alias);
                name = notEmpty(builder, split.get(1), "name", alias);
            } else {
                throw throwVersionCatalogProblemException(createVersionCatalogError(getInVersionCatalog(builder.getName()) + ", on alias '" + alias + "' module '" + mi + "' is not a valid module notation.", INVALID_MODULE_NOTATION)
                    .description("When using a string to declare library module coordinates, you must use a valid module notation")
                    .solution("Make sure that the module consist of 2 parts separated by colons, eg: my.group:artifact"));
            }
        }
        String versionRef = null;
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        if (version instanceof String) {
            require = (String) version;
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            expectedKeys(versionTable, VERSION_KEYS, "version declaration of alias '" + alias + "'");
            versionRef = notEmpty(builder, versionTable.getString("ref"), "version reference", alias);
            require = notEmpty(builder, versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(builder, versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(builder, versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray(builder, "alias", alias, versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                .map(String::valueOf)
                .map(v -> notEmpty(builder, v, "rejected version", alias))
                .collect(toList()) : null;
            rejectAll = expectBoolean(builder, "alias", alias, versionTable, "rejectAll");
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, builder, version);
        }
        if (group == null) {
            // ProblemIds for "subtypes" of a problem
            throw throwVersionCatalogAliasException(alias, "group");
        }
        if (name == null) {
            throw throwVersionCatalogAliasException(alias, "name");
        }
        registerDependency(builder, alias, group, name, versionRef, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    @Nonnull
    private static RuntimeException throwVersionCatalogAliasException(String alias, String aliasType) {
        return throwVersionCatalogProblemException(createAliasInvalid(alias)
            .description(capitalize(aliasType) + " for alias '" + alias + "' wasn't set")
            .solution("Add the '" + aliasType + "' element on alias '" + alias + "'"));
    }

    @Nonnull
    private static ProblemBuilder createAliasInvalid(String alias) {
        return createVersionCatalogError("Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR);
    }

    private static void parsePlugin(String alias, TomlTable librariesTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        Object coordinates = librariesTable.get(alias);
        if (coordinates instanceof String) {
            List<String> split = SPLITTER.splitToList((String) coordinates);
            if (split.size() == 2) {
                String id = notEmpty(builder, split.get(0), "id", alias);
                String version = notEmpty(builder, split.get(1), "version", alias);
                StrictVersionParser.RichVersion rich = strictVersionParser.parse(version);
                registerPlugin(builder, alias, id, null, rich.require, rich.strictly, rich.prefer, null, null);
                return;
            } else {
                throw throwVersionCatalogProblemException(createVersionCatalogError(getInVersionCatalog(builder.getName()) + ", on alias '" + alias + "' notation '" + coordinates + "' is not a valid plugin notation.", INVALID_PLUGIN_NOTATION)
                    .description("When using a string to declare plugin coordinates, you must use a valid plugin notation")
                    .solution("Make sure that the coordinates consist of 2 parts separated by colons, eg: my.plugin.id:1.2"));
            }
        }
        if (coordinates instanceof TomlTable) {
            expectedKeys((TomlTable) coordinates, PLUGIN_COORDINATES, "plugin declaration '" + alias + "'");
        }
        String id = expectString(builder, "alias", alias, librariesTable, "id");
        Object version = librariesTable.get(alias + ".version");
        String versionRef = null;
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        if (version instanceof String) {
            require = (String) version;
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            expectedKeys(versionTable, VERSION_KEYS, "version declaration of alias '" + alias + "'");
            versionRef = notEmpty(builder, versionTable.getString("ref"), "version reference", alias);
            require = notEmpty(builder, versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(builder, versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(builder, versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray(builder, "alias", alias, versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                .map(String::valueOf)
                .map(v -> notEmpty(builder, v, "rejected version", alias))
                .collect(toList()) : null;
            rejectAll = expectBoolean(builder, "alias", alias, versionTable, "rejectAll");
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, builder, version);
        }
        if (id == null) {
            throw throwVersionCatalogProblemException(createVersionCatalogError("Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                .description("Id for plugin alias '" + alias + "' wasn't set")
                .solution("Add the 'id' element on alias '" + alias + "'"));
        }
        registerPlugin(builder, alias, id, versionRef, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    private static RuntimeException throwUnexpectedVersionSyntax(String alias, VersionCatalogBuilder builder, Object version) {
        throw throwVersionCatalogProblemException(createVersionCatalogError("Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
            .description("expected a version as a String or a table but got " + version.getClass().getSimpleName())
            .solution("Use a String notation, e.g version = \"1.1\"")
            .solution("Use a version reference, e.g version.ref = \"some-version\"")
            .solution("Use a rich version table, e.g version = { require=\"[1.0, 2.0[\", prefer=\"1.5\" }"));
    }

    private static void parseVersion(String alias, TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        Object version = versionsTable.get(alias);
        if (version instanceof String) {
            require = notEmpty(builder, (String) version, "version", alias);
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            require = notEmpty(builder, versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(builder, versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(builder, versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray(builder, "alias", alias, versionTable, "reject");
            rejectedVersions = rejectedArray != null ? rejectedArray.toList().stream()
                .map(String::valueOf)
                .map(v -> notEmpty(builder, v, "rejected version", alias))
                .collect(toList()) : null;
            rejectAll = expectBoolean(builder, "alias", alias, versionTable, "rejectAll");
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, builder, version);
        }
        registerVersion(builder, alias, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    @Nullable
    private static String notEmpty(VersionCatalogBuilder builder, @Nullable String string, @Nullable String member, String alias) {
        if (string == null) {
            return null;
        }
        if (string.isEmpty()) {
            throw throwVersionCatalogProblemException(createVersionCatalogError("Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                .description("Empty " + member + " for plugin alias '" + alias + "'" +
                    (member == null ? "value" : capitalize(member)) + " for '" + alias + "' must not be empty")
                .solution("Remove the '" + member + "' element on alias '" + alias + "'"));
        }
        return string;
    }

    private static void registerDependency(
        VersionCatalogBuilder builder,
        String alias,
        String group,
        String name,
        @Nullable String versionRef,
        @Nullable String require,
        @Nullable String strictly,
        @Nullable String prefer,
        @Nullable List<String> rejectedVersions,
        @Nullable Boolean rejectAll
    ) {
        VersionCatalogBuilder.LibraryAliasBuilder aliasBuilder = builder.library(alias, group, name);
        if (versionRef != null) {
            aliasBuilder.versionRef(versionRef);
            return;
        }
        aliasBuilder.version(v -> configureVersion(require, strictly, prefer, rejectedVersions, rejectAll, v));
    }

    private static void configureVersion(
        @Nullable String require,
        @Nullable String strictly,
        @Nullable String prefer,
        @Nullable List<String> rejectedVersions,
        @Nullable Boolean rejectAll,
        MutableVersionConstraint v
    ) {
        if (require != null) {
            v.require(require);
        }
        if (strictly != null) {
            v.strictly(strictly);
        }
        if (prefer != null) {
            v.prefer(prefer);
        }
        if (rejectedVersions != null) {
            v.reject(rejectedVersions.toArray(new String[0]));
        }
        if (rejectAll != null && rejectAll) {
            v.rejectAll();
        }
    }

    private static void registerPlugin(
        VersionCatalogBuilder builder,
        String alias,
        String id,
        @Nullable String versionRef,
        @Nullable String require,
        @Nullable String strictly,
        @Nullable String prefer,
        @Nullable List<String> rejectedVersions,
        @Nullable Boolean rejectAll
    ) {
        VersionCatalogBuilder.PluginAliasBuilder aliasBuilder = builder.plugin(alias, id);
        if (versionRef != null) {
            aliasBuilder.versionRef(versionRef);
            return;
        }
        aliasBuilder.version(v -> configureVersion(require, strictly, prefer, rejectedVersions, rejectAll, v));
    }

    private static void registerVersion(
        VersionCatalogBuilder builder,
        String alias,
        @Nullable String require,
        @Nullable String strictly,
        @Nullable String prefer,
        @Nullable List<String> rejectedVersions,
        @Nullable Boolean rejectAll
    ) {
        builder.version(alias, v -> configureVersion(require, strictly, prefer, rejectedVersions, rejectAll, v));
    }

    private static RuntimeException throwVersionCatalogProblemException(ProblemBuilder problem) {
        throw throwErrorWithNewProblemsApi("Invalid TOML catalog definition", ImmutableList.of(problem.build()));
    }
}
