/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.FileUtils;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.management.VersionCatalogBuilderInternal;
import org.gradle.plugin.use.PluginDependenciesSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultVersionCatalogBuilder implements VersionCatalogBuilderInternal {
    private final static Logger LOGGER = Logging.getLogger(DefaultVersionCatalogBuilder.class);
    private final static Attribute<String> INTERNAL_COUNTER = Attribute.of("org.gradle.internal.dm.model.builder.id", String.class);
    private final static List<String> FORBIDDEN_ALIAS_SUFFIX = ImmutableList.of("bundles", "versions", "version", "bundle");

    private final Interner<String> strings;
    private final Interner<ImmutableVersionConstraint> versionConstraintInterner;
    private final ObjectFactory objects;
    private final ProviderFactory providers;
    private final PluginDependenciesSpec plugins;
    private final String name;
    private final Map<String, VersionModel> versionConstraints = Maps.newLinkedHashMap();
    private final Map<String, Supplier<DependencyModel>> dependencies = Maps.newLinkedHashMap();
    private final Map<String, BundleModel> bundles = Maps.newLinkedHashMap();
    private final Lazy<DefaultVersionCatalog> model = Lazy.unsafe().of(this::doBuild);
    private final Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier;
    private final List<Import> imports = Lists.newArrayList();
    private final StrictVersionParser strictVersionParser;
    private final Property<String> description;

    private String currentContext;

    @Inject
    public DefaultVersionCatalogBuilder(String name,
                                        Interner<String> strings,
                                        Interner<ImmutableVersionConstraint> versionConstraintInterner,
                                        ObjectFactory objects,
                                        ProviderFactory providers,
                                        PluginDependenciesSpec plugins,
                                        Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier) {
        this.name = name;
        this.strings = strings;
        this.versionConstraintInterner = versionConstraintInterner;
        this.objects = objects;
        this.providers = providers;
        this.plugins = plugins;
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

    private DefaultVersionCatalog doBuild() {
        maybeImportPlatforms();
        for (Map.Entry<String, BundleModel> entry : bundles.entrySet()) {
            String bundleName = entry.getKey();
            List<String> aliases = entry.getValue().getComponents();
            for (String alias : aliases) {
                if (!dependencies.containsKey(alias)) {
                    throw new InvalidUserDataException("A bundle with name '" + bundleName + "' declares a dependency on '" + alias + "' which doesn't exist");
                }
            }
        }
        ImmutableMap.Builder<String, DependencyModel> realizedDeps = ImmutableMap.builderWithExpectedSize(dependencies.size());
        for (Map.Entry<String, Supplier<DependencyModel>> entry : dependencies.entrySet()) {
            realizedDeps.put(entry.getKey(), entry.getValue().get());
        }
        return new DefaultVersionCatalog(name, description.getOrElse(""), realizedDeps.build(), ImmutableMap.copyOf(bundles), ImmutableMap.copyOf(versionConstraints));
    }

    private void maybeImportPlatforms() {
        if (imports.isEmpty()) {
            return;
        }
        DependencyResolutionServices drs = dependencyResolutionServicesSupplier.get();
        Configuration cnf = createResolvableConfiguration(drs);
        addImportsToResolvableConfiguration(drs, cnf);
        Map<ComponentIdentifier, Action<? super ImportSpec>> variantToImport = associateVariantToImportSpec(cnf);
        cnf.getIncoming().getArtifacts().getArtifacts().forEach(ar -> {
            File file = ar.getFile();
            Action<? super ImportSpec> configurationAction = variantToImport.getOrDefault(ar.getVariant().getOwner(), Actions.doNothing());
            withContext("catalog " + ar.getVariant().getOwner(), () -> {
                importCatalogFromFile(file, configurationAction);
            });
        });
    }

    private void addImportsToResolvableConfiguration(DependencyResolutionServices drs, Configuration cnf) {
        for (int i = 0, importsSize = imports.size(); i < importsSize; i++) {
            Import imported = imports.get(i);
            Object notation = imported.notation;
            Dependency dependency = drs.getDependencyHandler().create(notation);
            if (dependency instanceof HasConfigurableAttributes) {
                // This is a workaround for the resolved configuration API which doesn't let
                // us associate a resolved variant to its files, so we cannot associate directly
                // the import configuration to the imported file
                associateDependencyWithId(i, (HasConfigurableAttributes<?>) dependency);
            }
            cnf.getDependencies().add(dependency);
        }
    }

    private Configuration createResolvableConfiguration(DependencyResolutionServices drs) {
        Configuration cnf = drs.getConfigurationContainer().create("incomingPlatformsFor" + StringUtils.capitalize(name));
        cnf.getResolutionStrategy().activateDependencyLocking();
        cnf.attributes(attrs -> {
            attrs.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.REGULAR_PLATFORM));
            attrs.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.VERSION_CATALOG));
        });
        cnf.setCanBeResolved(true);
        cnf.setCanBeConsumed(false);
        return cnf;
    }

    private Map<ComponentIdentifier, Action<? super ImportSpec>> associateVariantToImportSpec(Configuration cnf) {
        Map<ComponentIdentifier, Action<? super ImportSpec>> mapping = Maps.newHashMap();
        cnf.getIncoming().getResolutionResult().getAllDependencies().stream()
            .filter(ResolvedDependencyResult.class::isInstance)
            .map(ResolvedDependencyResult.class::cast)
            .forEach(resolvedDependencyResult -> {
                AttributeContainer attributes = resolvedDependencyResult.getRequested().getAttributes();
                String cpt = attributes.getAttribute(INTERNAL_COUNTER);
                ComponentIdentifier owner = resolvedDependencyResult.getResolvedVariant().getOwner();
                Action<? super ImportSpec> action = mapping.getOrDefault(owner, Actions.doNothing());
                if (cpt != null) {
                    action = Actions.composite(action, imports.get(Integer.parseInt(cpt)).spec);
                    mapping.put(owner, action);
                }
            });
        return mapping;
    }

    private static void associateDependencyWithId(int id, HasConfigurableAttributes<?> dependency) {
        // We use a String attribute because when we get resolved metadata is coerced to a string in any case
        dependency.attributes(attrs -> attrs.attribute(INTERNAL_COUNTER, String.valueOf(id)));
    }

    @Override
    public void from(Object dependencyNotation, Action<? super ImportSpec> importSpec) {
        imports.add(new Import(dependencyNotation, importSpec));
    }

    private void importCatalogFromFile(File modelFile, Action<? super ImportSpec> configurationAction) {
        if (!FileUtils.hasExtensionIgnoresCase(modelFile.getName(), "toml")) {
            throw new InvalidUserDataException("Unsupported file format: please use a TOML file");
        }
        if (!modelFile.exists()) {
            throw new InvalidUserDataException("Catalog file " + modelFile + " doesn't exist");
        }
        RegularFileProperty srcProp = objects.fileProperty();
        srcProp.set(modelFile);
        Provider<byte[]> dataSource = providers.fileContents(srcProp).getAsBytes().forUseAtConfigurationTime();
        try {
            DefaultImportSpec spec = new DefaultImportSpec();
            configurationAction.execute(spec);
            TomlDependenciesFileParser.parse(new ByteArrayInputStream(dataSource.get()), this, plugins, spec.toConfiguration());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String version(String name, Action<? super MutableVersionConstraint> versionSpec) {
        validateName("name", name);
        if (versionConstraints.containsKey(name)) {
            // For versions, in order to allow overriding whatever is declared by
            // a platform, we want to silence overrides
            return name;
        }
        MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
        versionSpec.execute(versionBuilder);
        ImmutableVersionConstraint version = versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
        versionConstraints.put(name, new VersionModel(version, currentContext));
        return name;
    }

    @Override
    public String version(String name, String version) {
        StrictVersionParser.RichVersion richVersion = strictVersionParser.parse(version);
        version(name, vc -> {
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
        return name;
    }

    @Override
    public AliasBuilder alias(String alias) {
        validateName("alias", alias);
        return new DefaultAliasBuilder(alias);
    }

    private static void validateName(String type, String value) {
        if (!DependenciesModelHelper.ALIAS_PATTERN.matcher(value).matches()) {
            throw new InvalidUserDataException("Invalid " + type + " name '" + value + "': it must match the following regular expression: " + DependenciesModelHelper.ALIAS_REGEX);
        }
        if ("alias".equals(type)) {
            validateAlias(value);
        }
    }

    private static void validateAlias(String alias) {
        for (String suffix : FORBIDDEN_ALIAS_SUFFIX) {
            String sl = alias.toLowerCase();
            if (sl.endsWith(suffix)) {
                throw new InvalidUserDataException("Invalid alias name '" + alias + "': it must not end with '" + suffix + "'");
            }
        }
    }

    @Override
    public void bundle(String name, List<String> aliases) {
        validateName("bundle", name);
        ImmutableList<String> components = ImmutableList.copyOf(aliases.stream().map(this::intern).collect(Collectors.toList()));
        BundleModel previous = bundles.put(intern(name), new BundleModel(components, currentContext));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for bundle '{}': {} is replaced with {}", name, previous.getComponents(), components);
        }
    }

    @Nullable
    private String intern(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return strings.intern(value);
    }

    public boolean containsDependencyAlias(String name) {
        return dependencies.containsKey(name);
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
                throw new InvalidUserDataException("Referenced version '" + versionRef + "' doesn't exist on dependency " + group + ":" + name);
            }
            return new DependencyModel(group, name, versionRef, model.getVersion(), context);
        }
    }

    private class DefaultAliasBuilder implements AliasBuilder {
        private final String alias;

        public DefaultAliasBuilder(String alias) {
            this.alias = alias;
        }

        @Override
        public void to(String gavCoordinates) {
            String[] coordinates = gavCoordinates.split(":");
            if (coordinates.length == 3) {
                to(coordinates[0], coordinates[1]).version(coordinates[2]);
            } else {
                throw new InvalidUserDataException("Invalid dependency notation: it must consist of 3 parts separated by colons, eg: my.group:artifact:1.2");
            }
        }

        @Override
        public LibraryAliasBuilder to(String group, String name) {
            return objects.newInstance(DefaultLibraryAliasBuilder.class, DefaultVersionCatalogBuilder.this, alias, group, name);
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
            ImmutableVersionConstraint version = owner.versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
            DependencyModel model = new DependencyModel(owner.intern(group), owner.intern(name), null, version, owner.currentContext);
            Supplier<DependencyModel> previous = owner.dependencies.put(owner.intern(alias), () -> model);
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
            owner.createAliasWithVersionRef(alias, group, name, versionRef);
        }

        @Override
        public void withoutVersion() {
            version("");
        }
    }

    private void createAliasWithVersionRef(String alias, String group, String name, String versionRef) {
        Supplier<DependencyModel> previous = dependencies.put(intern(alias), new VersionReferencingDependencyModel(group, name, versionRef));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model);
        }
    }

    private static class Import {
        private final Action<? super ImportSpec> spec;
        private final Object notation;

        private Import(Object notation, Action<? super ImportSpec> spec) {
            this.spec = spec;
            this.notation = notation;
        }
    }

    private static class DefaultImportSpec implements ImportSpec {
        private Set<String> includedAliases;
        private Set<String> excludedAliases;

        private Set<String> includedBundles;
        private Set<String> excludedBundles;

        private Set<String> includedVersions;
        private Set<String> excludedVersions;

        private Set<String> includedPlugins;
        private Set<String> excludedPlugins;

        @Override
        public void includeDependency(String... aliases) {
            if (includedAliases == null) {
                includedAliases = Sets.newHashSet();
            }
            Collections.addAll(includedAliases, aliases);
        }

        @Override
        public void excludeDependency(String... aliases) {
            if (excludedAliases == null) {
                excludedAliases = Sets.newHashSet();
            }
            Collections.addAll(excludedAliases, aliases);
        }

        @Override
        public void includeBundle(String... bundles) {
            if (includedBundles == null) {
                includedBundles = Sets.newHashSet();
            }
            Collections.addAll(includedBundles, bundles);
        }

        @Override
        public void excludeBundle(String... bundles) {
            if (excludedBundles == null) {
                excludedBundles = Sets.newHashSet();
            }
            Collections.addAll(excludedBundles, bundles);
        }

        @Override
        public void includeVersion(String... aliases) {
            if (includedVersions == null) {
                includedVersions = Sets.newHashSet();
            }
            Collections.addAll(includedVersions, aliases);
        }

        @Override
        public void excludeVersion(String... aliases) {
            if (excludedVersions == null) {
                excludedVersions = Sets.newHashSet();
            }
            Collections.addAll(excludedVersions, aliases);
        }

        @Override
        public void includePlugin(String... ids) {
            if (includedPlugins == null) {
                includedPlugins = Sets.newHashSet();
            }
            Collections.addAll(includedPlugins, ids);
        }

        @Override
        public void excludePlugin(String... ids) {
            if (excludedPlugins == null) {
                excludedPlugins = Sets.newHashSet();
            }
            Collections.addAll(excludedPlugins, ids);
        }

        ImportConfiguration toConfiguration() {
            return new ImportConfiguration(
                IncludeExcludePredicate.of(includedAliases, excludedAliases),
                IncludeExcludePredicate.of(includedBundles, excludedBundles),
                IncludeExcludePredicate.of(includedVersions, excludedVersions),
                IncludeExcludePredicate.of(includedPlugins, excludedPlugins)
            );
        }
    }
}
