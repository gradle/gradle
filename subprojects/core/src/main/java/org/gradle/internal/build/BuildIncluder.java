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

package org.gradle.internal.build;

import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

/**
 * Coordinates inclusion of builds from the current build.
 */
@ServiceScope(Scopes.Build.class)
public interface BuildIncluder {
    IncludedBuildState includeBuild(IncludedBuildSpec includedBuildSpec);

    void registerPluginBuild(IncludedBuildSpec includedBuildSpec);

    Collection<IncludedBuildState> includeRegisteredPluginBuilds();
}
