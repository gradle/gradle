/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.util.GradleUtil;
import groovy.lang.GString;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
public class DefaultProjectDependencyFactory implements ProjectDependencyFactory {
    public ProjectDependency createProject(ProjectFinder projectFinder, Object notation) {
        assert notation != null;
        if (notation instanceof String || notation instanceof GString) {
            return new DefaultProjectDependency(projectFinder.getProject(notation.toString()));
        } else if (notation instanceof Map) {
            return createProjectFromMap(projectFinder, (Map<? extends String,? extends Object>) notation);
        }
        throw new IllegalDependencyNotation();
    }

    private ProjectDependency createProjectFromMap(ProjectFinder projectFinder, Map<? extends String,? extends Object> map) {
        Map<String, Object> args = new HashMap<String, Object>(map);
        String path = getAndRemove(args, "path");
        String configuration = getAndRemove(args, "configuration");
        ProjectDependency dependency = new DefaultProjectDependency(projectFinder.getProject(path), configuration);
        GradleUtil.setFromMap(dependency, args);
        return dependency;
    }

    private String getAndRemove(Map<String, Object> args, String key) {
        Object value = args.get(key);
        args.remove(key);
        return value != null ? value.toString() : null;
    }
}
