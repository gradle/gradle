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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DefaultCompositeBuildContext implements CompositeBuildContext {
    private final Multimap<ModuleIdentifier, String> replacementProjects = ArrayListMultimap.create();
    private final Map<String, LocalComponentMetaData> projectMetadata = Maps.newHashMap();
    private final Set<ModuleIdentifier> duplicateProjects = Sets.newHashSet();

    @Override
    public String getReplacementProject(ModuleComponentSelector selector) {
        ModuleIdentifier candidateId = DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule());
        Collection<String> providingProjects = replacementProjects.get(candidateId);
        if (providingProjects.isEmpty()) {
            return null;
        }
        if (providingProjects.size() == 1) {
            return providingProjects.iterator().next();
        }
        String failureMessage = String.format("Module version '%s' is not unique in composite: can be provided by projects %s.", selector.getDisplayName(), providingProjects);
        throw new ModuleVersionResolveException(selector, failureMessage);
    }

    @Override
    public LocalComponentMetaData getProject(String projectPath) {
        return projectMetadata.get(projectPath);
    }

    @Override
    public void register(ModuleIdentifier moduleId, String projectPath, LocalComponentMetaData localComponentMetaData) {
        System.out.println(String.format("Registering project participant: %s | %s", moduleId, projectPath));
        replacementProjects.put(moduleId, projectPath);
        projectMetadata.put(projectPath, localComponentMetaData);
    }
}
