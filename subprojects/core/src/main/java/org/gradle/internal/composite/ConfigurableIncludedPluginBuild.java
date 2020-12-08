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

package org.gradle.internal.composite;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitutions;

import javax.annotation.Nonnull;
import java.io.File;

public class ConfigurableIncludedPluginBuild extends DefaultConfigurableIncludedBuild {

    public ConfigurableIncludedPluginBuild(File projectDir) {
        super(projectDir);
    }

    @Override
    public void dependencySubstitution(@Nonnull Action<? super DependencySubstitutions> action) {
        throw new IllegalArgumentException("Included plugin builds can not have dependency substitutions");
    }

    boolean isPluginBuild() {
        return true;
    }
}
