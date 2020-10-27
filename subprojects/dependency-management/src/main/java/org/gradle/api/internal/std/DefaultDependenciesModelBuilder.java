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
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.management.DependenciesModelBuilderInternal;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultDependenciesModelBuilder implements DependenciesModelBuilderInternal {
    private final static Logger LOGGER = Logging.getLogger(DefaultDependenciesModelBuilder.class);

    private final Interner<String> strings;
    private final Interner<ImmutableVersionConstraint> versions;
    private final Map<String, DependencyModel> dependencies = Maps.newLinkedHashMap();
    private final Map<String, List<String>> bundles = Maps.newLinkedHashMap();
    private final Lazy<AllDependenciesModel> model = Lazy.unsafe().of(this::doBuild);
    private final String name;

    @Inject
    public DefaultDependenciesModelBuilder(String name, Interner<String> strings, Interner<ImmutableVersionConstraint> versions) {
        this.name = name;
        this.strings = strings;
        this.versions = versions;
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
        return new AllDependenciesModel(name, ImmutableMap.copyOf(dependencies), ImmutableMap.copyOf(bundles));
    }

    @Override
    public void alias(String alias, String group, String name, Action<? super MutableVersionConstraint> versionSpec) {
        validateName("alias", alias);
        MutableVersionConstraint versionBuilder = new DefaultMutableVersionConstraint("");
        versionSpec.execute(versionBuilder);
        ImmutableVersionConstraint version = versions.intern(DefaultImmutableVersionConstraint.of(versionBuilder));
        DependencyModel model = new DependencyModel(intern(group), intern(name), version);
        DependencyModel previous = dependencies.put(intern(alias), model);
        if (previous != null) {
            LOGGER.warn("Duplicate entry for alias '{}': {} is replaced with {}", alias, previous, model);
        }
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
}
