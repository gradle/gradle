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

import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DependencyBundleValueSource implements ValueSource<ExternalModuleDependencyBundle, DependencyBundleValueSource.Params> {

    interface Params extends ValueSourceParameters {
        Property<String> getBundleName();

        Property<AllDependenciesModel> getConfig();
    }

    @Override
    public ExternalModuleDependencyBundle obtain() {
        String bundle = getParameters().getBundleName().get();
        AllDependenciesModel config = getParameters().getConfig().get();
        List<String> aliases = config.getBundle(bundle);
        return aliases.stream()
            .map(config::getDependencyData)
            .map(this::createDependency)
            .collect(Collectors.toCollection(DefaultBundle::new));

    }

    private DefaultMinimalDependency createDependency(DependencyModel data) {
        DependencyVersionModel version = data.getVersion();
        MutableVersionConstraint vc = DependenciesFactoryHelper.createVersionConstraint(
            version.getRequiredVersion(),
            version.getPreferredVersion(),
            version.getStrictlyVersion(),
            version.getRejectedVersions(),
            version.getRejectAll());
        return new DefaultMinimalDependency(
            DefaultModuleIdentifier.newId(data.getGroup(), data.getName()), vc
        );
    }

    private static class DefaultBundle extends ArrayList<MinimalExternalModuleDependency> implements ExternalModuleDependencyBundle {
    }

}
