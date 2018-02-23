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

package org.gradle.vcs.internal.spec;

import com.google.common.base.Preconditions;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.definition.DefaultInjectedPluginDependencies;
import org.gradle.vcs.VersionControlSpec;

import java.io.File;

public abstract class AbstractVersionControlSpec implements VersionControlSpec {
    private String rootDir = "";
    private final StartParameter rootBuildStartParameter;
    private final DefaultInjectedPluginDependencies pluginDependencies;

    protected AbstractVersionControlSpec(StartParameter rootBuildStartParameter, ClassLoaderScope classLoaderScope) {
        this.rootBuildStartParameter = rootBuildStartParameter;
        this.pluginDependencies = new DefaultInjectedPluginDependencies(classLoaderScope);
    }

    @Override
    public String getRootDir() {
        return rootDir;
    }

    @Override
    public void setRootDir(String rootDir) {
        Preconditions.checkNotNull(rootDir, "rootDir should be non-null for '%s'.", getDisplayName());
        this.rootDir = rootDir;
    }

    @Override
    public void plugins(Action<? super InjectedPluginDependencies> configuration) {
        configuration.execute(pluginDependencies);
    }

    public BuildDefinition getBuildDefinition(File buildDirectory) {
        return BuildDefinition.fromStartParameterForBuild(rootBuildStartParameter, buildDirectory, pluginDependencies.getRequests());
    }
}
