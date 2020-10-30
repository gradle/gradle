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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.management.DependenciesModelBuilderInternal;
import org.gradle.plugin.use.PluginDependenciesSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultDependenciesModelBuilder implements DependenciesModelBuilderInternal {
    private final static Logger LOGGER = Logging.getLogger(DefaultDependenciesModelBuilder.class);

    private final Interner<String> strings;
    private final Interner<ImmutableVersionConstraint> versionConstraintInterner;
    private final ObjectFactory objects;
    private final ProviderFactory providers;
    private final PluginDependenciesSpec plugins;
    private final String name;
    private final Map<String, ImmutableVersionConstraint> versionConstraints = Maps.newLinkedHashMap();
    private final Map<String, Supplier<DependencyModel>> dependencies = Maps.newLinkedHashMap();
    private final Map<String, List<String>> bundles = Maps.newLinkedHashMap();
    private final Lazy<AllDependenciesModel> model = Lazy.unsafe().of(this::doBuild);
    private final StrictVersionParser strictVersionParser;

    @Inject
    public DefaultDependenciesModelBuilder(String name,
                                           Interner<String> strings,
                                           Interner<ImmutableVersionConstraint> versionConstraintInterner,
                                           ObjectFactory objects,
                                           ProviderFactory providers,
                                           PluginDependenciesSpec plugins) {
        this.name = name;
        this.strings = strings;
        this.versionConstraintInterner = versionConstraintInterner;
        this.objects = objects;
        this.providers = providers;
        this.plugins = plugins;
        this.strictVersionParser = new StrictVersionParser(strings);
    }

    @Override
    public String getLibrariesExtensionName() {
        return name;
    }

    @Override
    public AllDependenciesModel build() {
        return model.get();
    }

    private AllDependenciesModel doBuild() {
        for (Map.Entry<String, List<String>> entry : bundles.entrySet()) {
            String bundleName = entry.getKey();
            List<String> aliases = entry.getValue();
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
        return new AllDependenciesModel(name, realizedDeps.build(), ImmutableMap.copyOf(bundles), ImmutableMap.copyOf(versionConstraints));
    }

    @Override
    public void from(File modelFile) {
        if (!FileUtils.hasExtensionIgnoresCase(modelFile.getName(), "toml")) {
            throw new InvalidUserDataException("Unsupported file format: please use a TOML file");
        }
        RegularFileProperty srcProp = objects.fileProperty();
        srcProp.set(modelFile);
        Provider<byte[]> dataSource = providers.fileContents(srcProp).getAsBytes().forUseAtConfigurationTime();
        try {
            TomlDependenciesFileParser.parse(new ByteArrayInputStream(dataSource.get()), this, plugins);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String version(String name, Action<? super MutableVersionConstraint> versionSpec) {
        validateName("name", name);
        MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
        versionSpec.execute(versionBuilder);
        ImmutableVersionConstraint version = versionConstraintInterner.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
        versionConstraints.put(name, version);
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
    }

    @Override
    public void bundle(String name, List<String> aliases) {
        validateName("bundle", name);
        ImmutableList<String> value = ImmutableList.copyOf(aliases.stream().map(this::intern).collect(Collectors.toList()));
        List<String> previous = bundles.put(intern(name), value);
        if (previous != null) {
            LOGGER.warn("Duplicate entry for bundle '{}': {} is replaced with {}", name, previous, value);
        }
    }

    private String intern(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return strings.intern(value);
    }

    private class VersionReferencingDependencyModel implements Supplier<DependencyModel> {
        private final String group;
        private final String name;
        private final String versionRef;

        private VersionReferencingDependencyModel(String group, String name, String versionRef) {
            this.group = group;
            this.name = name;
            this.versionRef = versionRef;
        }

        @Override
        public DependencyModel get() {
            ImmutableVersionConstraint constraint = versionConstraints.get(versionRef);
            if (constraint == null) {
                throw new InvalidUserDataException("Referenced version '" + versionRef + "' doesn't exist on dependency " + group + ":" + name);
            }
            return new DependencyModel(group, name, constraint);
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
            return objects.newInstance(DefaultLibraryAliasBuilder.class, DefaultDependenciesModelBuilder.this, alias, group, name);
        }
    }

    // static public for injection!
    public static class DefaultLibraryAliasBuilder implements LibraryAliasBuilder {
        private final DefaultDependenciesModelBuilder owner;
        private final String alias;
        private final String group;
        private final String name;

        @Inject
        public DefaultLibraryAliasBuilder(DefaultDependenciesModelBuilder owner, String alias, String group, String name) {
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
            DependencyModel model = new DependencyModel(owner.intern(group), owner.intern(name), version);
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
    }

    private void createAliasWithVersionRef(String alias, String group, String name, String versionRef) {
        Supplier<DependencyModel> previous = dependencies.put(intern(alias), new VersionReferencingDependencyModel(group, name, versionRef));
        if (previous != null) {
            LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous.get(), model);
        }
    }
}
