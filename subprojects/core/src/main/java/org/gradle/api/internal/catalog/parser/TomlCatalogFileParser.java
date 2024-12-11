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

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interners;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.api.problems.internal.InternalProblemSpec;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.logging.text.TreeFormatter;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.capitalize;
import static org.apache.commons.lang.StringUtils.uncapitalize;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.VERSION_CATALOG_PROBLEMS;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.getInVersionCatalog;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.getProblemInVersionCatalog;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.throwError;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_MODULE_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_PLUGIN_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOML_SYNTAX_ERROR;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNSUPPORTED_FORMAT_VERSION;
import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.RenderingUtils.quotedOxfordListOf;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.util.internal.TextUtil.getPluralEnding;
import static org.gradle.util.internal.TextUtil.screamingSnakeToKebabCase;

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
    public static final String INVALID_TOML_CATALOG_DEFINITION = "Invalid TOML catalog definition";
    private final Path catalogFilePath;
    private final VersionCatalogBuilder versionCatalogBuilder;
    private final Supplier<Problems> problemsServiceSupplier;

    public TomlCatalogFileParser(Path catalogFilePath, VersionCatalogBuilder builder, Supplier<Problems> problemsServiceSupplier) {
        this.catalogFilePath = catalogFilePath;
        this.versionCatalogBuilder = builder;
        this.problemsServiceSupplier = problemsServiceSupplier;
    }

    public static void parse(Path catalogFilePath, VersionCatalogBuilder builder, Supplier<Problems> problemsServiceSupplier) throws IOException {
        new TomlCatalogFileParser(catalogFilePath, builder, problemsServiceSupplier).parse();
    }

    private void parse() throws IOException {
        StrictVersionParser strictVersionParser = new StrictVersionParser(Interners.newStrongInterner());
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(catalogFilePath))) {
            TomlParseResult result = Toml.parse(inputStream);
            assertNoParseErrors(result);
            TomlTable metadataTable = result.getTable(METADATA_KEY);
            verifyMetadata(metadataTable);
            TomlTable librariesTable = result.getTable(LIBRARIES_KEY);
            TomlTable bundlesTable = result.getTable(BUNDLES_KEY);
            TomlTable versionsTable = result.getTable(VERSIONS_KEY);
            TomlTable pluginsTable = result.getTable(PLUGINS_KEY);
            Sets.SetView<String> unknownTle = Sets.difference(result.keySet(), TOP_LEVEL_ELEMENTS);
            if (!unknownTle.isEmpty()) {
                throw throwVersionCatalogProblemException(builder ->
                    configureVersionCatalogError(builder, getProblemInVersionCatalog(versionCatalogBuilder) + ", unknown top level elements " + unknownTle, TOML_SYNTAX_ERROR)
                        .details("TOML file contains an unexpected top-level element")
                        .solution("Make sure the top-level elements of your TOML file is one of " + quotedOxfordListOf(TOP_LEVEL_ELEMENTS, "or")));
            }
            parseLibraries(librariesTable, strictVersionParser);
            parsePlugins(pluginsTable, strictVersionParser);
            parseBundles(bundlesTable);
            parseVersions(versionsTable, strictVersionParser);
        }
    }

    private InternalProblemReporter getInternalReporter() {
        return getInternalProblems().getInternalReporter();
    }

    @Nonnull
    private static ProblemSpec configureVersionCatalogError(InternalProblemSpec builder, String message, VersionCatalogProblemId catalogProblemId) {
        return configureVersionCatalogError(builder, message, catalogProblemId, input -> input);
    }

    private static InternalProblemSpec configureVersionCatalogError(InternalProblemSpec builder, String label, VersionCatalogProblemId catalogProblemId, Function<InternalProblemSpec, InternalProblemSpec> locationDefiner) {
        InternalProblemSpec definingLocation = builder
            .id(screamingSnakeToKebabCase(catalogProblemId.name()), "Dependency version catalog problem", GradleCoreProblemGroup.versionCatalog())
            .contextualLabel(label)
            .documentedAt(userManual(VERSION_CATALOG_PROBLEMS, catalogProblemId.name().toLowerCase(Locale.ROOT)));
        InternalProblemSpec definingCategory = locationDefiner.apply(definingLocation);
        return definingCategory
            .severity(ERROR);
    }

    private void assertNoParseErrors(TomlParseResult result) {
        if (result.hasErrors()) {
            List<TomlParseError> errors = result.errors();
            reportErrors(errors);

            TreeFormatter formatter = new TreeFormatter();
            formatter.node(INVALID_TOML_CATALOG_DEFINITION);
            formatter.startChildren();
            formatter.node(getProblemString(errors));
            formatter.endChildren();
            throw new InvalidUserDataException(formatter.toString());
        }
    }

    private void reportErrors(List<TomlParseError> errors) {
        InternalProblemReporter internalReporter = getInternalReporter();
        errors.stream().map(error -> internalReporter.internalCreate(builder ->
            configureVersionCatalogError(builder, error.getMessage(), TOML_SYNTAX_ERROR, definingLocation -> {
                definingLocation.lineInFileLocation(catalogFilePath.toAbsolutePath().toString(), error.position().line(), error.position().column());
                return definingLocation;
            })
                .details("TOML syntax invalid.")
                .solution("Fix the TOML file according to the syntax described at https://toml.io")
        )).forEach(internalReporter::report);
    }

    private String getProblemString(List<TomlParseError> errors) {
        return DefaultCatalogProblemBuilder.getProblemString(
            getProblemInVersionCatalog(versionCatalogBuilder) + ", parsing failed with " + errors.size() + " error" + getPluralEnding(errors) + ".",
            getErrorText(errors),
            ImmutableList.of("Fix the TOML file according to the syntax described at https://toml.io"),
            userManual(VERSION_CATALOG_PROBLEMS, TOML_SYNTAX_ERROR.name().toLowerCase(Locale.ROOT)));
    }

    private String getErrorText(List<TomlParseError> errors) {
        return errors.stream().map(error -> "In file '" +
                catalogFilePath.toAbsolutePath() +
                "' at line " +
                error.position().line() + ", column " +
                error.position().column() +
                ": " +
                error.getMessage())
            .collect(joining("\n"));
    }

    private InternalProblems getInternalProblems() {
        return (InternalProblems) problemsServiceSupplier.get();
    }

    private void verifyMetadata(@Nullable TomlTable metadataTable) {
        if (metadataTable != null) {
            String format = metadataTable.getString("format.version");
            if (format != null && !CURRENT_VERSION.equals(format)) {
                throw throwVersionCatalogProblemException(builder ->
                    configureVersionCatalogError(builder, getProblemInVersionCatalog(versionCatalogBuilder) + ", unsupported version catalog format " + format + ".", UNSUPPORTED_FORMAT_VERSION)
                        .details("This version of Gradle only supports format version " + CURRENT_VERSION)
                        .solution("Try to upgrade to a newer version of Gradle which supports the catalog format version " + format + "."));
            }
        }
    }

    private void parseLibraries(@Nullable TomlTable librariesTable, StrictVersionParser strictVersionParser) {
        if (librariesTable == null) {
            return;
        }
        librariesTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parseLibrary(alias, librariesTable, versionCatalogBuilder, strictVersionParser));
    }

    private void parsePlugins(@Nullable TomlTable pluginsTable, StrictVersionParser strictVersionParser) {
        if (pluginsTable == null) {
            return;
        }
        pluginsTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parsePlugin(alias, pluginsTable, versionCatalogBuilder, strictVersionParser));
    }

    private void parseVersions(@Nullable TomlTable versionsTable, StrictVersionParser strictVersionParser) {
        if (versionsTable == null) {
            return;
        }
        versionsTable.keySet()
            .stream()
            .sorted(comparing(String::length))
            .forEach(alias -> parseVersion(alias, versionsTable, versionCatalogBuilder, strictVersionParser));
    }

    private void parseBundles(@Nullable TomlTable bundlesTable) {
        if (bundlesTable == null) {
            return;
        }
        bundlesTable.keySet()
            .stream()
            .sorted()
            .forEach(alias -> {
                List<String> bundled = expectArray("bundle", alias, bundlesTable, alias).toList().stream()
                    .map(String::valueOf)
                    .collect(toList());
                versionCatalogBuilder.bundle(alias, bundled);
            });
    }

    @Nullable
    private String expectString(String name, TomlTable table, @Nullable String element) {
        try {
            String path = name;
            if (element != null) {
                path += "." + element;
            }
            return notEmpty(table.getString(path), element, name);
        } catch (TomlInvalidTypeException ex) {
            throw throwUnexpectedTypeError("alias", name, "a string", ex);
        }
    }

    private RuntimeException throwUnexpectedTypeError(String kind, String name, String typeLabel, TomlInvalidTypeException ex) {
        throw throwVersionCatalogProblemException(builder ->
            configureVersionCatalogError(builder, "Unexpected type for " + kind + " '" + name + "'", TOML_SYNTAX_ERROR)
                .details("Expected " + typeLabel + " but " + uncapitalize(ex.getMessage()))
                .solution("Use " + typeLabel + " instead"));
    }

    @Nullable
    private TomlArray expectArray(String kind, String alias, TomlTable table, String element) {
        try {
            return table.getArray(element);
        } catch (TomlInvalidTypeException ex) {
            throw throwUnexpectedTypeError(kind, alias, "an array", ex);
        }
    }

    @Nullable
    private Boolean getBooleanRejectAll(String alias, TomlTable table) {
        try {
            return table.getBoolean("rejectAll");
        } catch (TomlInvalidTypeException ex) {
            throw throwUnexpectedTypeError("alias", alias, "a boolean", ex);
        }
    }

    private static void expectedKeys(TomlTable table, Set<String> allowedKeys, String context) {
        Set<String> actualKeys = table.keySet();
        if (!allowedKeys.containsAll(actualKeys)) {
            Set<String> difference = Sets.difference(actualKeys, allowedKeys);
            throw new InvalidUserDataException("On " + context + " expected to find any of " + quotedOxfordListOf(allowedKeys, "or")
                + " but found unexpected key" + getPluralEnding(difference) + " " + quotedOxfordListOf(difference, "and")
                + ".");
        }
    }

    private void parseLibrary(String alias, TomlTable librariesTable, VersionCatalogBuilder versionCatalogBuilder, StrictVersionParser strictVersionParser) {
        Object gav = librariesTable.get(alias);
        if (gav instanceof String) {
            List<String> split = SPLITTER.splitToList((String) gav);
            if (split.size() != 3) {
                throw throwVersionCatalogProblemException(builder -> {
                    configureVersionCatalogError(builder, getInVersionCatalog(versionCatalogBuilder.getName()) + ", on alias '" + alias + "' notation '" + gav + "' is not a valid dependency notation.", INVALID_DEPENDENCY_NOTATION)
                        .details("When using a string to declare library coordinates, you must use a valid dependency notation")
                        .solution("Make sure that the coordinates consist of 3 parts separated by colons, e.g.: my.group:artifact:1.2");
                    if (split.size() == 2) {
                        builder.solution("To declare without a version, use '" + alias + ".module' instead, i.e.: " + alias + ".module = \"" + gav + "\"");
                    }
                });
            }
            String group = notEmpty(split.get(0), "group", alias);
            String name = notEmpty(split.get(1), "name", alias);
            String version = notEmpty(split.get(2), "version", alias);
            StrictVersionParser.RichVersion rich = strictVersionParser.parse(version);
            registerDependency(versionCatalogBuilder, alias, group, name, null, rich.require, rich.strictly, rich.prefer, null, null);
            return;
        }
        if (gav instanceof TomlTable) {
            expectedKeys((TomlTable) gav, LIBRARY_COORDINATES, "library declaration '" + alias + "'");
        }
        String group = expectString(alias, librariesTable, "group");
        String name = expectString(alias, librariesTable, "name");
        Object version = librariesTable.get(alias + ".version");
        String mi = expectString(alias, librariesTable, "module");
        if (mi != null) {
            List<String> split = SPLITTER.splitToList(mi);
            if (split.size() == 2) {
                group = notEmpty(split.get(0), "group", alias);
                name = notEmpty(split.get(1), "name", alias);
            } else {
                throw throwVersionCatalogProblemException(builder ->
                    configureVersionCatalogError(builder, getInVersionCatalog(versionCatalogBuilder.getName()) + ", on alias '" + alias + "' module '" + mi + "' is not a valid module notation.", INVALID_MODULE_NOTATION)
                        .details("When using a string to declare library module coordinates, you must use a valid module notation")
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
            versionRef = notEmpty(versionTable.getString("ref"), "version reference", alias);
            require = notEmpty(versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray("alias", alias, versionTable, "reject");
            rejectedVersions = getRejectedVersions(rejectedArray, alias);
            rejectAll = getBooleanRejectAll(alias, versionTable);
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, version);
        }
        if (group == null) {
            // ProblemIds for "subtypes" of a problem
            throw throwVersionCatalogAliasException(alias, "group");
        }
        if (name == null) {
            throw throwVersionCatalogAliasException(alias, "name");
        }
        registerDependency(versionCatalogBuilder, alias, group, name, versionRef, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    @Nonnull
    private RuntimeException throwVersionCatalogAliasException(String alias, String aliasType) {
        throw throwVersionCatalogProblemException(builder ->
            configureVersionCatalogError(builder, "Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                .details(capitalize(aliasType) + " for alias '" + alias + "' wasn't set")
                .solution("Add the '" + aliasType + "' element on alias '" + alias + "'"));
    }

    private void parsePlugin(String alias, TomlTable librariesTable, VersionCatalogBuilder versionCatalogBuilder, StrictVersionParser strictVersionParser) {
        Object coordinates = librariesTable.get(alias);
        if (coordinates instanceof String) {
            List<String> split = SPLITTER.splitToList((String) coordinates);
            if (split.size() == 2) {
                String id = notEmpty(split.get(0), "id", alias);
                String version = notEmpty(split.get(1), "version", alias);
                StrictVersionParser.RichVersion rich = strictVersionParser.parse(version);
                registerPlugin(versionCatalogBuilder, alias, id, null, rich.require, rich.strictly, rich.prefer, null, null);
                return;
            } else {
                throw throwVersionCatalogProblemException(builder ->
                    configureVersionCatalogError(builder, getInVersionCatalog(versionCatalogBuilder.getName()) + ", on alias '" + alias + "' notation '" + coordinates + "' is not a valid plugin notation.", INVALID_PLUGIN_NOTATION)
                        .details("When using a string to declare plugin coordinates, you must use a valid plugin notation")
                        .solution("Make sure that the coordinates consist of 2 parts separated by colons, eg: my.plugin.id:1.2"));
            }
        }
        if (coordinates instanceof TomlTable) {
            expectedKeys((TomlTable) coordinates, PLUGIN_COORDINATES, "plugin declaration '" + alias + "'");
        }
        String id = expectString(alias, librariesTable, "id");
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
            versionRef = notEmpty(versionTable.getString("ref"), "version reference", alias);
            require = notEmpty(versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray("alias", alias, versionTable, "reject");
            rejectedVersions = getRejectedVersions(rejectedArray, alias);
            rejectAll = getBooleanRejectAll(alias, versionTable);
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, version);
        }
        if (id == null) {
            throw throwVersionCatalogProblemException(builder ->
                configureVersionCatalogError(builder, "Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                    .details("Id for plugin alias '" + alias + "' wasn't set")
                    .solution("Add the 'id' element on alias '" + alias + "'"));
        }
        registerPlugin(versionCatalogBuilder, alias, id, versionRef, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    private RuntimeException throwUnexpectedVersionSyntax(String alias, Object version) {
        throw throwVersionCatalogProblemException(builder ->
            configureVersionCatalogError(builder, "Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                .details("expected a version as a String or a table but got " + version.getClass().getSimpleName())
                .solution("Use a String notation, e.g version = \"1.1\"")
                .solution("Use a version reference, e.g version.ref = \"some-version\"")
                .solution("Use a rich version table, e.g version = { require=\"[1.0, 2.0[\", prefer=\"1.5\" }"));
    }

    private void parseVersion(String alias, TomlTable versionsTable, VersionCatalogBuilder builder, StrictVersionParser strictVersionParser) {
        String require = null;
        String strictly = null;
        String prefer = null;
        List<String> rejectedVersions = null;
        Boolean rejectAll = null;
        Object version = versionsTable.get(alias);
        if (version instanceof String) {
            require = notEmpty((String) version, "version", alias);
            StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(require);
            require = richVersion.require;
            prefer = richVersion.prefer;
            strictly = richVersion.strictly;
        } else if (version instanceof TomlTable) {
            TomlTable versionTable = (TomlTable) version;
            require = notEmpty(versionTable.getString("require"), "required version", alias);
            prefer = notEmpty(versionTable.getString("prefer"), "preferred version", alias);
            strictly = notEmpty(versionTable.getString("strictly"), "strict version", alias);
            TomlArray rejectedArray = expectArray("alias", alias, versionTable, "reject");
            rejectedVersions = getRejectedVersions(rejectedArray, alias);
            rejectAll = getBooleanRejectAll(alias, versionTable);
        } else if (version != null) {
            throw throwUnexpectedVersionSyntax(alias, version);
        }
        registerVersion(builder, alias, require, strictly, prefer, rejectedVersions, rejectAll);
    }

    @Nullable
    private List<String> getRejectedVersions(TomlArray rejectedArray, String alias) {
        if (rejectedArray == null) {
            return null;
        }
        return rejectedArray.toList().stream()
            .map(String::valueOf)
            .map(v -> notEmpty(v, "rejected version", alias))
            .collect(toList());
    }

    @Nullable
    private String notEmpty(@Nullable String string, @Nullable String member, String alias) {
        if (string == null) {
            return null;
        }
        if (string.isEmpty()) {
            throw throwVersionCatalogProblemException(builder ->
                configureVersionCatalogError(builder, "Alias definition '" + alias + "' is invalid", TOML_SYNTAX_ERROR)
                    .details("Empty " + member + " for plugin alias '" + alias + "'" +
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

    private RuntimeException throwVersionCatalogProblemException(Action<InternalProblemSpec> action) {
        throw throwError(getInternalProblems(), INVALID_TOML_CATALOG_DEFINITION, ImmutableList.of(getInternalReporter().internalCreate(action)));
    }
}
