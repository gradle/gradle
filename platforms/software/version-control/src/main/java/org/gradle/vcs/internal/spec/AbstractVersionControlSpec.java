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
import org.gradle.api.Action;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.initialization.definition.DefaultInjectedPluginDependencies;
import org.gradle.initialization.definition.DefaultInjectedPluginDependency;
import org.gradle.vcs.VersionControlSpec;

import java.util.List;

public abstract class AbstractVersionControlSpec implements VersionControlSpec {
    private String rootDir = "";
    private final DefaultInjectedPluginDependencies pluginDependencies = new DefaultInjectedPluginDependencies();

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

    public List<DefaultInjectedPluginDependency> getInjectedPlugins() {
        return pluginDependencies.getDependencies();
    }
}
