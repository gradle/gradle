/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.initialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ServiceScope(Scope.Settings.class)
public class DefaultProjectDescriptorRegistry implements ProjectDescriptorRegistry {
    private final Map<String, DefaultProjectDescriptor> projects = new HashMap<>();

    @Override
    public void addProject(DefaultProjectDescriptor project) {
        DefaultProjectDescriptor previous = projects.put(project.getPath(), project);
        if (previous != null) {
            throw new IllegalArgumentException(String.format("Multiple projects registered for path '%s'.", project.getPath()));
        }
    }

    public DefaultProjectDescriptor removeProject(String path) {
        DefaultProjectDescriptor project = projects.remove(path);
        assert project != null;
        return project;
    }

    @Override
    public int size() {
        return projects.size();
    }

    @Override
    public Set<DefaultProjectDescriptor> getAllProjects() {
        return ImmutableSet.copyOf(projects.values());
    }

    @Override
    public DefaultProjectDescriptor getRootProject() {
        return getProject(Path.ROOT.getPath());
    }

    @Override
    public @Nullable DefaultProjectDescriptor getProject(String path) {
        return projects.get(path);
    }

    @Override
    public void changeDescriptorPath(Path oldPath, Path newPath) {
        DefaultProjectDescriptor projectDescriptor = removeProject(oldPath.toString());
        projectDescriptor.setPath(newPath);
        addProject(projectDescriptor);
    }

}
