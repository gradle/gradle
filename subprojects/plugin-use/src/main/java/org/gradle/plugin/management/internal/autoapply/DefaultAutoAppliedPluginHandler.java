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
package org.gradle.plugin.management.internal.autoapply;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.ArrayList;
import java.util.List;

public class DefaultAutoAppliedPluginHandler implements AutoAppliedPluginHandler {

    private final AutoAppliedPluginRegistry registry;

    public DefaultAutoAppliedPluginHandler(AutoAppliedPluginRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PluginRequests mergeWithAutoAppliedPlugins(PluginRequests initialRequests, Object pluginTarget) {
        if (!(pluginTarget instanceof Project)) {
            return initialRequests;
        }
        Project project = (Project) pluginTarget;

        PluginRequests autoAppliedPlugins = registry.getAutoAppliedPlugins(project);
        if (autoAppliedPlugins.isEmpty()) {
            return initialRequests;
        }

        List<PluginRequestInternal> filteredAutoAppliedPlugins = filterAlreadyAppliedOrRequested(autoAppliedPlugins, initialRequests, project);
        List<PluginRequestInternal> merged = new ArrayList<PluginRequestInternal>(initialRequests.size() + autoAppliedPlugins.size());
        merged.addAll(filteredAutoAppliedPlugins);
        merged.addAll(ImmutableList.copyOf(initialRequests));
        return new DefaultPluginRequests(merged);
    }

    private List<PluginRequestInternal> filterAlreadyAppliedOrRequested(PluginRequests autoAppliedPlugins, final PluginRequests initialRequests, final Project project) {
        return Lists.newArrayList(Iterables.filter(autoAppliedPlugins, new Predicate<PluginRequestInternal>() {
            @Override
            public boolean apply(PluginRequestInternal autoAppliedPlugin) {
                return !isAlreadyAppliedOrRequested(autoAppliedPlugin, initialRequests, project);
            }
        }));
    }

    private static boolean isAlreadyAppliedOrRequested(PluginRequestInternal autoAppliedPlugin, PluginRequests requests, Project project) {
        return isAlreadyApplied(autoAppliedPlugin, project) || isAlreadyRequestedInPluginsBlock(autoAppliedPlugin, requests) || isAlreadyRequestedInBuildScriptBlock(autoAppliedPlugin, project);
    }

    private static boolean isAlreadyApplied(PluginRequestInternal autoAppliedPlugin, Project project) {
        return project.getPlugins().hasPlugin(autoAppliedPlugin.getId().getId());
    }

    private static boolean isAlreadyRequestedInPluginsBlock(PluginRequestInternal autoAppliedPlugin, PluginRequests requests) {
        for (PluginRequestInternal request : requests) {
            if (autoAppliedPlugin.getId().equals(request.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlreadyRequestedInBuildScriptBlock(PluginRequestInternal autoAppliedPlugin, Project project) {
        ModuleVersionSelector module = autoAppliedPlugin.getModule();
        if (module == null) {
            return false;
        }

        Configuration classpathConfiguration = project.getBuildscript().getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION);
        for (Dependency dependency : classpathConfiguration.getDependencies()) {
            if (module.getGroup().equals(dependency.getGroup()) && module.getName().equals(dependency.getName())) {
                return true;
            }
        }

        return false;
    }
}
