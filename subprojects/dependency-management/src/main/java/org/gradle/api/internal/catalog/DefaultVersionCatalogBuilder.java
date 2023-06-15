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
package org.gradle.api.internal.catalog;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.catalog.parser.DependenciesModelHelper;
import org.gradle.api.internal.catalog.parser.StrictVersionParser;
import org.gradle.api.internal.catalog.parser.TomlCatalogFileParser;
import org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemBuilder;
import org.gradle.api.internal.catalog.problems.VersionCatalogProblemId;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.provider.Property;
import org.gradle.internal.FileUtils;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.management.VersionCatalogBuilderInternal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.api.internal.catalog.parser.DependenciesModelHelper.ALIAS_REGEX;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.buildProblem;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.createVersionCatalogError;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.maybeThrowError;
import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.throwErrorWithNewProblemsApi;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.ALIAS_NOT_FINISHED;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.CATALOG_FILE_DOES_NOT_EXIST;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_ALIAS_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_DEPENDENCY_NOTATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.NO_IMPORT_FILES;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.RESERVED_ALIAS_NAME;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOO_MANY_IMPORT_FILES;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.TOO_MANY_IMPORT_INVOCATION;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.UNSUPPORTED_FILE_FORMAT;
import static org.gradle.problems.internal.RenderingUtils.oxfordListOf;

public class DefaultVersionCatalogBuilder implements VersionCatalogBuilderInternal {

    private enum AliasType {
        LIBRARY,
        PLUGIN,
        BUNDLE,
        VERSION,
        // To be removed.
        ALIAS;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final static Logger LOGGER = Logging.getLogger(DefaultVersionCatalogBuilder.class);
    private final static List<String> FORBIDDEN_LIBRARY_ALIAS_PREFIX = ImmutableList.of("bundles", "versions", "plugins");
    private final static Set<String> RESERVED_ALIAS_NAMES = ImmutableSet.of("extensions", "convention");
    /**
     * names that are forbidden in generated accessors because we can't override getClass()
     */
    private final static Set<String> RESERVED_JAVA_NAMES = ImmutableSet.of("class");

    private final Interner<String> strings;
    private final Interner<ImmutableVersionConstraint> versionConstraintInterner;
    private final ObjectFactory objects;
    private final String name;
    private final Map<String, VersionModel> versionConstraints = Maps.newLinkedHashMap();
    private final Map<String, Supplier<DependencyModel>> libraries = Maps.newLinkedHashMap();
    /**
     * Aliases that are being constructed, used to detect unfinished builders.
     */
    private final Set<String> aliasesInProgress = Sets.newLinkedHashSet();
    private final Map<String, Supplier<PluginModel>> plugins = Maps.newLinkedHashMap();
    private final Map<String, BundleModel> bundles = Maps.newLinkedHashMap();
    private final Lazy<DefaultVersionCatalog> model = Lazy.unsafe().of(this::doBuild);
    private final Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier;
    private Import importedCatalog = null;
    private final StrictVersionParser strictVersionParser;
    private final Property<String> description;

    private String currentContext;

    @Inject
    public DefaultVersionCatalogBuilder(
        String name,
        Interner<String> strings,
        Interner<ImmutableVersionConstraint> versionConstraintInterner,
        ObjectFactory objects,
        Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier
    ) {
        this.name = name;
        this.strings = strings;
        this.versionConstraintInterner = versionConstraintInterner;
        this.objects = objects;
        this.dependencyResolutionServicesSupplier = dependencyResolutionServicesSupplier;
        this.strictVersionParser = new StrictVersionParser(strings);
        this.description = objects.property(String.class).convention("A catalog of dependencies accessible via the `" + name + "` extension.");
    }

    @Override
    public String getLibrariesExtensionName() {
        return name;
    }

    @Override
    public Property<String> getDescription() {
        return description;
    }

    @Override
    public DefaultVersionCatalog build() {
        return model.get();
    }

    public void withContext(String context, Runnable action) {
        String oldContext = currentContext;
        currentContext = intern(context);
        try {
            action.run();
        } finally {
            currentContext = oldContext;
        }
    }

    private static RuntimeException throwVersionCatalogProblemException(ProblemBuilder problem) {
        throw throwErrorWithNewProblemsApi("Invalid catalog definition", ImmutableList.of(problem.build()));
    }

    private DefaultVersionCatalog doBuild() {
        maybeImportCatalogs();
        if (!aliasesInProgress.isEmpty()) {
            String alias = aliasesInProgress.iterator().next();
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "dependency alias builder '" + alias + "' was not finished.", ALIAS_NOT_FINISHED)
                .description("A version was not set or explicitly declared as not wanted")
                .solution("Call `.version()` to give the alias a version")
                .solution("Call `.withoutVersion()` to explicitly declare that the alias should not have a version"));


//            throw throwVersionCatalogProblem(ALIAS_NOT_FINISHED, spec ->
//                spec.withShortDescription(() -> "Dependency alias builder '" + alias + "' was not finished.")
//                    .happensBecause("A version was not set or explicitly declared as not wanted")
//                    .addSolution("Call `.version()` to give the alias a version")
//                    .addSolution("Call `.withoutVersion()` to explicitly declare that the alias should not have a version")
//                    .documented());
        }
        for (Map.Entry<String, BundleModel> entry : bundles.entrySet()) {
            String bundleName = entry.getKey();
            List<String> aliases = entry.getValue().getComponents();
            for (String alias : aliases) {
                if (!libraries.containsKey(alias)) {
                    throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "a bundle with name '" + bundleName + "' declares a dependency on '" + alias +
                        "' which doesn't exist.", UNDEFINED_ALIAS_REFERENCE)
                        .description("Bundles can only contain references to existing library aliases.")
                        .solution("Make sure that the library alias '" + alias + "' is declared")
                        .solution("Remove '" + alias + "' from bundle '" + bundleName + "'."));

//                    throw throwVersionCatalogProblem(VersionCatalogProblemId.UNDEFINED_ALIAS_REFERENCE, spec ->
//                        spec.withShortDescription(() -> "A bundle with name '" + bundleName + "' declares a dependency on '" + alias + "' which doesn't exist")
//                            .happensBecause("Bundles can only contain references to existing library aliases")
//                            .addSolution(() -> "Make sure that the library alias '" + alias + "' is declared")
//                            .addSolution(() -> "Remove '" + alias + "' from bundle '" + bundleName + "'")
//                            .documented()
//                    );
                }
            }
        }
        ImmutableMap.Builder<String, DependencyModel> realizedLibs = ImmutableMap.builderWithExpectedSize(libraries.size());
        for (Map.Entry<String, Supplier<DependencyModel>> entry : libraries.entrySet()) {
            realizedLibs.put(entry.getKey(), entry.getValue().get());
        }
        ImmutableMap.Builder<String, PluginModel> realizedPlugins = ImmutableMap.builderWithExpectedSize(plugins.size());
        for (Map.Entry<String, Supplier<PluginModel>> entry : plugins.entrySet()) {
            realizedPlugins.put(entry.getKey(), entry.getValue().get());
        }
        return new DefaultVersionCatalog(name, description.getOrElse(""), realizedLibs.build(), ImmutableMap.copyOf(bundles), ImmutableMap.copyOf(versionConstraints), realizedPlugins.build());
    }

    @Nonnull
    private String getProblemInVersionCatalog() {
        return DefaultCatalogProblemBuilder.getProblemInVersionCatalog(name) + ", ";
    }

    private void maybeImportCatalogs() {
        if (importedCatalog == null) {
            return;
        }
        DependencyResolutionServices drs = dependencyResolutionServicesSupplier.get();
        Configuration cnf = createResolvableConfiguration(drs);
        addImportsToResolvableConfiguration(drs, cnf, importedCatalog);

        Set<ResolvedArtifactResult> artifacts = cnf.getIncoming().getArtifacts().getArtifacts();
        if (artifacts.size() > 1) {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "Importing multiple files are not supported", TOO_MANY_IMPORT_FILES)
                .description("The import consists of multiple files")
                .solution("Only import a single file"));

//            throw throwVersionCatalogProblem(TOO_MANY_IMPORT_FILES, spec ->
//                spec.withShortDescription("Importing multiple files are not supported")
//                    .happensBecause("The import consists of multiple files")
//                    .addSolution("Only import a single file")
//                    .documented()
//            );
        }

        // We need to fall back to if-else with the Optional, as the Problems API cannot return an instance of an exception, only throw
        Optional<ResolvedArtifactResult> maybeResolvedArtifactResult = artifacts.stream().findFirst();
        if (maybeResolvedArtifactResult.isPresent()) {
            ResolvedArtifactResult resolvedArtifactResult = maybeResolvedArtifactResult.get();
            File file = resolvedArtifactResult.getFile();
            withContext("catalog " + resolvedArtifactResult.getVariant().getOwner(), () -> importCatalogFromFile(file));
        } else {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "No files are resolved to be imported", NO_IMPORT_FILES)
                .description("The imported dependency doesn't resolve into any file")
                .solution("Check the import statement, it should resolve into a single file"));
//                throwVersionCatalogProblem(NO_IMPORT_FILES, spec ->
//                spec.withShortDescription("No files are resolved to be imported")
//                    .happensBecause("The imported dependency doesn't resolve into any file")
//                    .addSolution("Check the import statement, it should resolve into a single file")
//                    .documented()
//            );
        }
    }

    private void addImportsToResolvableConfiguration(DependencyResolutionServices drs, Configuration cnf, Import imported) {
        Object notation = imported.notation;
        Dependency dependency = drs.getDependencyHandler().create(notation);
        cnf.getDependencies().add(dependency);
    }

    private Configuration createResolvableConfiguration(DependencyResolutionServices drs) {
        // The zero at the end of the configuration comes from the previous implementation;
        // Multiple files could be imported, and all members of the list were given their own configuration, postfixed by the index in the array.
        // After moving this into a single-file import, we didn't want to break the lock files generated for the configuration, so we simply kept the zero.
        Configuration cnf = ((RoleBasedConfigurationContainerInternal) drs.getConfigurationContainer()).resolvableBucket("incomingCatalogFor" + StringUtils.capitalize(name) + "0");
        cnf.getResolutionStrategy().activateDependencyLocking();
        cnf.attributes(attrs -> {
            attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.REGULAR_PLATFORM));
            attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.VERSION_CATALOG));
        });
        return cnf;
    }

    @Override
    public void from(Object dependencyNotation) {
        if (importedCatalog == null) {
            importedCatalog = new Import(dependencyNotation);
        } else {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "You can only call the 'from' method a single time", TOO_MANY_IMPORT_INVOCATION)
                .description("The method was called more than once")
                .solution("Remove further usages of the method call"));

//                throw throwVersionCatalogProblem(TOO_MANY_IMPORT_INVOCATION, spec ->
//                spec.withShortDescription("You can only call the 'from' method a single time")
//                    .happensBecause("The method was called more than once")
//                    .addSolution("Remove further usages of the method call")
//                    .documented()
//            );
        }
    }

    private void importCatalogFromFile(File modelFile) {
        if (!FileUtils.hasExtensionIgnoresCase(modelFile.getName(), "toml")) {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "File " + modelFile.getName() + " isn't a supported", UNSUPPORTED_FILE_FORMAT)
                .description("Only .toml files are allowed when importing catalogs")
                .solution("Use a TOML file instead, with the .toml extension"));

//            throw throwVersionCatalogProblem(UNSUPPORTED_FILE_FORMAT, spec ->
//                spec.withShortDescription(() -> "File " + modelFile.getName() + " isn't a supported")
//                    .happensBecause("Only .toml files are allowed when importing catalogs")
//                    .addSolution("Use a TOML file instead, with the .toml extension")
//                    .documented()
//            );
        }
        if (!modelFile.exists()) {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "Import of external catalog file failed", CATALOG_FILE_DOES_NOT_EXIST)
                .description("File '" + modelFile + "' doesn't exist")
                .solution("Make sure that the catalog file '" + modelFile.getName() + "' exists before importing it"));

//            throw throwVersionCatalogProblem(CATALOG_FILE_DOES_NOT_EXIST, spec ->
//                spec.withShortDescription(() -> "Import of external catalog file failed")
//                    .happensBecause(() -> "File '" + modelFile + "' doesn't exist")
//                    .addSolution(() -> "Make sure that the catalog file '" + modelFile.getName() + "' exists before importing it")
//                    .documented()
//            );
        }

        Instrumented.fileOpened(modelFile, getClass().getName());
        try {
            TomlCatalogFileParser.parse(modelFile.toPath(), this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String version(String alias, Action<? super MutableVersionConstraint> versionSpec) {
        validateAlias(AliasType.VERSION, alias);
        alias = intern(AliasNormalizer.normalize(alias));
        if (versionConstraints.containsKey(alias)) {
            // For versions, in order to allow overriding whatever is declared by
            // a platform, we want to silence overrides
            return alias;
        }
        MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
        versionSpec.execute(versionBuilder);
        ImmutableVersionConstraint version = versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
        versionConstraints.put(alias, new VersionModel(version, currentContext));
        return alias;
    }

    @Override
    public String version(String alias, String version) {
        StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(version);
        version(alias, vc -> {
            if (richVersion.require != null) {
                vc.require(richVersion.require);
            }
            if (richVersion.prefer != null) {
                vc.prefer(richVersion.prefer);
            }
            if (richVersion.strictly != null) {
                vc.strictly(richVersion.strictly);
            }
        });
        return alias;
    }

    @Override
    public LibraryAliasBuilder library(String alias, String group, String artifact) {
        String normalizedAlias = normalizeAndValidateAlias(AliasType.LIBRARY, alias);
        return objects.newInstance(DefaultLibraryAliasBuilder.class, DefaultVersionCatalogBuilder.this, normalizedAlias, group, artifact);
    }

    @Override
    public void library(String alias, String groupArtifactVersion) {
        String normalizedAlias = normalizeAndValidateAlias(AliasType.LIBRARY, alias);

        String[] coordinates = groupArtifactVersion.split(":");
        if (coordinates.length == 3) {
            objects.newInstance(DefaultLibraryAliasBuilder.class, DefaultVersionCatalogBuilder.this, normalizedAlias, coordinates[0], coordinates[1]).version(coordinates[2]);
        } else {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "on alias '" + alias + "' notation '" + groupArtifactVersion + "' is not a valid dependency notation.", INVALID_DEPENDENCY_NOTATION)
                .description("The 'to(String)' method only supports 'group:artifact:version' coordinates")
                .solution("Make sure that the coordinates consist of 3 parts separated by colons, eg: my.group:artifact:1.2")
                .solution("Use the to(group, name) method instead"));

//            throw throwVersionCatalogProblem(INVALID_DEPENDENCY_NOTATION, spec ->
//                spec.withShortDescription(() -> "On alias '" + alias + "' notation '" + groupArtifactVersion + "' is not a valid dependency notation")
//                    .happensBecause(() -> "The 'to(String)' method only supports 'group:artifact:version' coordinates")
//                    .addSolution("Make sure that the coordinates consist of 3 parts separated by colons, eg: my.group:artifact:1.2")
//                    .addSolution("Use the to(group, name) method instead")
//                    .documented());
        }
    }

    @Override
    public PluginAliasBuilder plugin(String alias, String id) {
        String normalizedAlias = normalizeAndValidateAlias(AliasType.PLUGIN, alias);
        return objects.newInstance(DefaultPluginAliasBuilder.class, DefaultVersionCatalogBuilder.this, normalizedAlias, id);
    }

    private String normalizeAndValidateAlias(AliasType type, String alias) {
        validateAlias(type, alias);
        String normalizedAlias = AliasNormalizer.normalize(alias);
        validateNormalizedAlias(type, alias, normalizedAlias);
        return normalizedAlias;
    }

    private void validateNormalizedAlias(AliasType type, String alias, String normalizedAlias) {
        if (!aliasesInProgress.add(normalizedAlias)) {
            LOGGER.warn("Duplicate alias builder registered for {}", normalizedAlias);
        }

        if (type == AliasType.LIBRARY) {
            for (String prefix : FORBIDDEN_LIBRARY_ALIAS_PREFIX) {
                if (normalizedAlias.equals(prefix) || normalizedAlias.startsWith(prefix + ".")) {
                    throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "alias '" + alias + "' is not a valid alias.", RESERVED_ALIAS_NAME)
                        .description("Prefix for dependency shouldn't be equal to '" + prefix + "'")
                        .solution("Use a different alias which prefix is not equal to " + oxfordListOf(FORBIDDEN_LIBRARY_ALIAS_PREFIX, "or")));

//                    throw throwVersionCatalogProblem(RESERVED_ALIAS_NAME, spec ->
//                        spec.withShortDescription(() -> "Alias '" + alias + "' is not a valid alias")
//                            .happensBecause(() -> "Prefix for dependency shouldn't be equal to '" + prefix + "'")
//                            .addSolution(() -> "Use a different alias which prefix is not equal to " + oxfordListOf(FORBIDDEN_LIBRARY_ALIAS_PREFIX, "or"))
//                            .documented()
//                    );
                }
            }
        }
        if (RESERVED_ALIAS_NAMES.contains(normalizedAlias)) {
            throw throwAliasCatalogException(alias, RESERVED_ALIAS_NAMES);

//            throw throwVersionCatalogProblem(RESERVED_ALIAS_NAME, spec ->
//                spec.withShortDescription(() -> "Alias '" + alias + "' is not a valid alias")
//                    .happensBecause(() -> "Alias '" + alias + "' is a reserved name in Gradle which prevents generation of accessors")
//                    .addSolution(() -> "Use a different alias which isn't in the reserved names " + oxfordListOf(RESERVED_ALIAS_NAMES, "or"))
//                    .documented()
//            );
        }

        for (String name : normalizedAlias.split("\\.")) {
            if (RESERVED_JAVA_NAMES.contains(name)) {
                throw throwAliasCatalogException(alias, RESERVED_JAVA_NAMES);

//                throw throwVersionCatalogProblem(RESERVED_ALIAS_NAME, spec ->
//                    spec.withShortDescription(() -> "Alias '" + alias + "' is not a valid alias")
//                        .happensBecause(() -> "Alias '" + alias + "' contains a reserved name in Gradle and prevents generation of accessors")
//                        .addSolution(() -> "Use a different alias which doesn't contain " + getExcludedNames(RESERVED_JAVA_NAMES))
//                        .documented()
//                );
            }
        }
    }

    @Nonnull
    private RuntimeException throwAliasCatalogException(String alias, Collection<String> reservedNames) {
        return throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "alias '" + alias + "' is not a valid alias.", RESERVED_ALIAS_NAME)
            .description("Alias '" + alias + "' is a reserved name in Gradle which prevents generation of accessors.")
            .solution("Use a different alias which doesn't contain " + getExcludedNames(reservedNames) + "."));
    }

    @Nonnull
    public static String getExcludedNames(Collection<String> reservedNames) {
        String namesOrName = oxfordListOf(reservedNames, "or");
        if(reservedNames.size() == 1) {
            return namesOrName;
        }
        return "any of " + namesOrName;
    }

    private void validateAlias(AliasType type, String value) {
        if (!DependenciesModelHelper.ALIAS_PATTERN.matcher(value).matches()) {
            throw throwVersionCatalogProblemException(createVersionCatalogError(getProblemInVersionCatalog() + "invalid " + type + " alias '" + value + "'.", INVALID_ALIAS_NOTATION)
                .description(type + " aliases must match the following regular expression: "  + ALIAS_REGEX)
                .solution("Make sure the alias matches the " + ALIAS_REGEX + " regular expression"));

//            throw throwVersionCatalogProblem(INVALID_ALIAS_NOTATION, spec ->
//                spec.withShortDescription(() -> "Invalid " + type + " alias '" + value + "'")
//                    .happensBecause(() -> type + " aliases must match the following regular expression: " + DependenciesModelHelper.ALIAS_REGEX)
//                    .addSolution(() -> "Make sure the alias matches the " + DependenciesModelHelper.ALIAS_REGEX + " regular expression")
//                    .documented()
//            );
        }
    }

    private RuntimeException throwVersionCatalogProblem(VersionCatalogProblemId id, Consumer<? super VersionCatalogProblemBuilder.ProblemWithId> spec) {
        maybeThrowError("Invalid catalog definition", ImmutableList.of(
            buildProblem(id, pb -> spec.accept(pb.inContext(() -> "version catalog " + name))))
        );
        return null;
    }

    @Override
    public void bundle(String alias, List<String> aliases) {
        validateAlias(AliasType.BUNDLE, alias);
        ImmutableList<String> components = ImmutableList.copyOf(aliases.stream()
            .map(AliasNormalizer::normalize)
            .map(this::intern)
            .collect(Collectors.toList()));
        BundleModel previous = bundles.put(AliasNormalizer.normalize(intern(alias)), new BundleModel(components, currentContext));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for bundle '{}': {} is replaced with {}", alias, previous.getComponents(), components);
        }
    }

    @Nullable
    private String intern(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return strings.intern(value);
    }

    public boolean containsLibraryAlias(String name) {
        return libraries.containsKey(name);
    }

    @Override
    public String getName() {
        return name;
    }

    private class VersionReferencingDependencyModel implements Supplier<DependencyModel> {
        private final String group;
        private final String name;
        private final String versionRef;
        private final String context;

        private VersionReferencingDependencyModel(String group, String name, String versionRef) {
            this.group = group;
            this.name = name;
            this.versionRef = versionRef;
            this.context = currentContext;
        }

        @Override
        public DependencyModel get() {
            VersionModel model = versionConstraints.get(versionRef);
            if (model == null) {
                ProblemBuilder problemBuilder = createVersionCatalogError(getProblemInVersionCatalog() + "version reference '" + versionRef + "' doesn't exist.", UNDEFINED_VERSION_REFERENCE)
                    .description("Dependency '" + group + ":" + name + "' references version '" + versionRef + "' which doesn't exist")
                    .solution("Declare '" + versionRef + "' in the catalog");

                if (!versionConstraints.keySet().isEmpty()) {
                    problemBuilder.solution("Use one of the following existing versions: " + oxfordListOf(versionConstraints.keySet(), "or"));
                }

                throw throwVersionCatalogProblemException(problemBuilder);

//                throw throwVersionCatalogProblem(VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE, spec -> {
//                        VersionCatalogProblemBuilder.DescribedProblemWithCause solutions = spec.withShortDescription(() -> "Version reference '" + versionRef + "' doesn't exist")
//                            .happensBecause("Dependency '" + group + ":" + name + "' references version '" + versionRef + "' which doesn't exist")
//                            .addSolution(() -> "Declare '" + versionRef + "' in the catalog")
//                            .documented();
//                        if (!versionConstraints.keySet().isEmpty()) {
//                            solutions.addSolution(() -> "Use one of the following existing versions: " + oxfordListOf(versionConstraints.keySet(), "or"));
//                        }
//                    }
//                );
            } else {
                return new DependencyModel(group, name, versionRef, model.getVersion(), context);
            }
        }
    }

    private class VersionReferencingPluginModel implements Supplier<PluginModel> {
        private final String id;
        private final String versionRef;
        private final String context;

        private VersionReferencingPluginModel(String id, String versionRef) {
            this.id = id;
            this.versionRef = versionRef;
            this.context = currentContext;
        }

        @Override
        public PluginModel get() {
            VersionModel model = versionConstraints.get(versionRef);
            if (model == null) {
                ProblemBuilder problemBuilder = createVersionCatalogError(getProblemInVersionCatalog() + "version reference '" + versionRef + "' doesn't exist.", UNDEFINED_VERSION_REFERENCE)
                    .description("Plugin '" + id + "' references version '" + versionRef + "' which doesn't exist")
                    .solution("Declare '" + versionRef + "' in the catalog");
                if (!versionConstraints.keySet().isEmpty()) {
                    problemBuilder.solution("Use one of the following existing versions: " + oxfordListOf(versionConstraints.keySet(), "or"));
                }

                throw throwVersionCatalogProblemException(problemBuilder);

//                throw throwVersionCatalogProblem(VersionCatalogProblemId.UNDEFINED_VERSION_REFERENCE, spec -> {
//                        VersionCatalogProblemBuilder.DescribedProblemWithCause solutions = spec.withShortDescription(() -> "Version reference '" + versionRef + "' doesn't exist")
//                            .happensBecause("Plugin '" + id + "' references version '" + versionRef + "' which doesn't exist")
//                            .addSolution(() -> "Declare '" + versionRef + "' in the catalog")
//                            .documented();
//                        if (!versionConstraints.keySet().isEmpty()) {
//                            solutions.addSolution(() -> "Use one of the following existing versions: " + oxfordListOf(versionConstraints.keySet(), "or"));
//                        }
//                    }
//                );
            } else {
                return new PluginModel(id, versionRef, model.getVersion(), context);
            }
        }
    }

    // static public for injection!
    public static class DefaultLibraryAliasBuilder implements LibraryAliasBuilder {
        private final DefaultVersionCatalogBuilder owner;
        private final String alias;
        private final String group;
        private final String name;

        @Inject
        public DefaultLibraryAliasBuilder(DefaultVersionCatalogBuilder owner, String alias, String group, String name) {
            this.owner = owner;
            this.alias = alias;
            this.group = group;
            this.name = name;
        }

        @Override
        public void version(Action<? super MutableVersionConstraint> versionSpec) {
            MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
            versionSpec.execute(versionBuilder);
            owner.aliasesInProgress.remove(alias);
            ImmutableVersionConstraint version = owner.versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
            DependencyModel model = new DependencyModel(owner.intern(group), owner.intern(name), null, version, owner.currentContext);
            Supplier<DependencyModel> previous = owner.libraries.put(owner.intern(alias), () -> model);
            if (previous != null) {
                LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model);
            }
        }

        @Override
        public void version(String version) {
            StrictVersionParser.RichVersion richVersion = owner.strictVersionParser.parse(version);
            version(vc -> {
                if (richVersion.require != null) {
                    vc.require(richVersion.require);
                }
                if (richVersion.prefer != null) {
                    vc.prefer(richVersion.prefer);
                }
                if (richVersion.strictly != null) {
                    vc.strictly(richVersion.strictly);
                }
            });
        }

        @Override
        public void versionRef(String versionRef) {
            owner.aliasesInProgress.remove(alias);
            owner.createAliasWithVersionRef(alias, group, name, versionRef);
        }

        @Override
        public void withoutVersion() {
            version("");
        }
    }

    // static public for injection!
    public static class DefaultPluginAliasBuilder implements PluginAliasBuilder {
        private final DefaultVersionCatalogBuilder owner;
        private final String alias;
        private final String id;

        @Inject
        public DefaultPluginAliasBuilder(DefaultVersionCatalogBuilder owner, String alias, String id) {
            this.owner = owner;
            this.alias = alias;
            this.id = id;
        }

        @Override
        public void version(Action<? super MutableVersionConstraint> versionSpec) {
            MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
            versionSpec.execute(versionBuilder);
            owner.aliasesInProgress.remove(alias);
            ImmutableVersionConstraint version = owner.versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
            PluginModel model = new PluginModel(owner.intern(id), null, version, owner.currentContext);
            Supplier<PluginModel> previous = owner.plugins.put(owner.intern(alias), () -> model);
            if (previous != null) {
                LOGGER.warn("Duplicate entry for plugin '{}': {} is replaced with {}", alias, previous.get(), model);
            }
        }

        @Override
        public void version(String version) {
            StrictVersionParser.RichVersion richVersion = owner.strictVersionParser.parse(version);
            version(vc -> {
                if (richVersion.require != null) {
                    vc.require(richVersion.require);
                }
                if (richVersion.prefer != null) {
                    vc.prefer(richVersion.prefer);
                }
                if (richVersion.strictly != null) {
                    vc.strictly(richVersion.strictly);
                }
            });
        }

        @Override
        public void versionRef(String versionRef) {
            owner.aliasesInProgress.remove(alias);
            owner.createPluginAliasWithVersionRef(alias, id, versionRef);
        }
    }

    private void createAliasWithVersionRef(String alias, String group, String name, String versionRef) {
        Supplier<DependencyModel> previous = libraries.put(intern(AliasNormalizer.normalize(alias)), new VersionReferencingDependencyModel(group, name, AliasNormalizer.normalize(versionRef)));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model);
        }
    }

    private void createPluginAliasWithVersionRef(String alias, String id, String versionRef) {
        Supplier<PluginModel> previous = plugins.put(intern(AliasNormalizer.normalize(alias)), new VersionReferencingPluginModel(id, AliasNormalizer.normalize(versionRef)));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for plugin '{}': {} is replaced with {}", alias, previous.get(), model);
        }
    }

    private static class Import {
        private final Object notation;

        private Import(Object notation) {
            this.notation = notation;
        }
    }
}
