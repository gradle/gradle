/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.vcs;

import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * Captures user-provided information about a version control repository.
 *
 * @since 4.4
 */
public interface VersionControlSpec extends Describable {
    /**
     * Returns a {@link String} identifier which will be unique to this version
     * control specification among other version control specifications.
     */
    @ReplacesEagerProperty
    Provider<String> getUniqueId();

    /**
     * Returns the name of the repository.
     */
    @ReplacesEagerProperty
    Provider<String> getRepoName();

    /**
     * The relative path to the root of the build within the repository.
     *
     * <p>An empty string means the root of the repository (this is the default value).
     *
     * @return the root directory of the build, relative to the root of this repository.
     * @since 4.5
     */
    @ReplacesEagerProperty
    Property<String> getRootDir();

    /**
     * Configure injected plugins into this build.
     *
     * @param configuration the configuration action for adding injected plugins
     * @since 4.6
     */
    void plugins(Action<? super InjectedPluginDependencies> configuration);
}
