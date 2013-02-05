/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.notations;

import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.util.ConfigureUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyFactory {
    private final DefaultProjectDependencyFactory factory;

    public ProjectDependencyFactory(DefaultProjectDependencyFactory factory) {
        this.factory = factory;
    }

    //TODO SF turn into notation parser
    public ProjectDependency createFromMap(ProjectFinder projectFinder,
                                           Map<? extends String, ? extends Object> map) {
        Map<String, Object> args = new HashMap<String, Object>(map);
        String path = getAndRemove(args, "path");
        String configuration = getAndRemove(args, "configuration");
        ProjectDependency dependency = factory.create(projectFinder.getProject(path), configuration);
        ConfigureUtil.configureByMap(args, dependency);
        return dependency;
    }

    private String getAndRemove(Map<String, Object> args, String key) {
        Object value = args.get(key);
        args.remove(key);
        return value != null ? value.toString() : null;
    }
}
