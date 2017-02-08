/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.management;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.plugin.repository.PluginRepositoriesSpec;

@Incubating
public interface PluginManagementSpec {

    /**
     * Defines repositories to download artifacts from.
     *
     * @param repositoriesAction spec to configure {@link RepositoryHandler}
     * @since 3.5
     */
    void repositories(Action<? super PluginRepositoriesSpec> repositoriesAction);

    void repositories(@DelegatesTo(value = PluginRepositoriesSpec.class, strategy = Closure.DELEGATE_FIRST) Closure closure);

    PluginResolutionStrategy getPluginResolutionStrategy();

}
