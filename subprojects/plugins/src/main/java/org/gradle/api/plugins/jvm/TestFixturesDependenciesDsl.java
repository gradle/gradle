/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.ProjectTestFixtures;

import javax.annotation.Nullable;

import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_CAPABILITY_APPENDIX;

/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures">Test Fixtures</a> in {@code dependencies} blocks.
 *
 * @since 7.6
 */
@Incubating
public interface TestFixturesDependenciesDsl extends DependenciesDsl {

    default ExternalModuleDependency testFixtures(CharSequence dependencyNotation) {
        return testFixtures(getDependencyFactory().create(dependencyNotation));
    }

    default ExternalModuleDependency testFixtures(@Nullable String group, String name, @Nullable String version) {
        return testFixtures(getDependencyFactory().create(group, name, version));
    }

    default ExternalModuleDependency testFixtures(ExternalModuleDependency dependency) {
        dependency.capabilities(capabilities -> {
            capabilities.requireCapability(new ImmutableCapability(dependency.getGroup(), dependency.getName() + TEST_FIXTURES_CAPABILITY_APPENDIX, null));
        });
        return dependency;
    }

    default MinimalExternalModuleDependency testFixtures(MinimalExternalModuleDependency dependency) {
        dependency.capabilities(capabilities -> {
            capabilities.requireCapability(new ImmutableCapability(dependency.getGroup(), dependency.getName() + TEST_FIXTURES_CAPABILITY_APPENDIX, null));
        });
        return dependency;
    }

    default ProjectDependency testFixtures(ProjectDependency dependency) {
        dependency.capabilities(new ProjectTestFixtures(dependency.getDependencyProject()));
        return dependency;
    }

    default Provider<? extends MinimalExternalModuleDependency> testFixtures(ProviderConvertible<? extends MinimalExternalModuleDependency> dependency) {
        return dependency.asProvider().map(this::testFixtures);
    }

    default Provider<? extends ExternalModuleDependency> testFixtures(Provider<? extends ExternalModuleDependency> dependency) {
        return dependency.map(this::testFixtures);
    }
}
