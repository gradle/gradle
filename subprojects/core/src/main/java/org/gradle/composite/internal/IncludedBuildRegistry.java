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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.NestedBuildFactory;

import java.io.File;
import java.util.Map;

public interface IncludedBuildRegistry {
    boolean hasIncludedBuilds();
    Map<File, IncludedBuild> getIncludedBuilds();
    IncludedBuild getBuild(BuildIdentifier name);

    void validateExplicitIncludedBuilds(SettingsInternal settings);

    ConfigurableIncludedBuild addExplicitBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory);
    ConfigurableIncludedBuild addImplicitBuild(File buildDirectory, NestedBuildFactory nestedBuildFactory);
}
