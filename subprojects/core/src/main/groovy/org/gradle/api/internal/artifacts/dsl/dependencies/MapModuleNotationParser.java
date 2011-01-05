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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.util.ConfigureUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
class MapModuleNotationParser implements IDependencyImplementationFactory {
    private final ClassGenerator classGenerator;

    MapModuleNotationParser(ClassGenerator classGenerator) {
        this.classGenerator = classGenerator;
    }

    public <T extends Dependency> T createDependency(Class<T> type, Object userDependencyDescription)
            throws IllegalDependencyNotation {
        Map<String, Object> args = new HashMap<String, Object>((Map<String, ?>) userDependencyDescription);
        String group = getAndRemove(args, "group");
        String name = getAndRemove(args, "name");
        String version = getAndRemove(args, "version");
        String configuration = getAndRemove(args, "configuration");
        ExternalDependency dependency = (ExternalDependency) classGenerator.newInstance(type, group, name, version, configuration);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency, getAndRemove(args, "ext"), getAndRemove(args,
                "classifier"));
        ConfigureUtil.configureByMap(args, dependency);
        return type.cast(dependency);
    }

    private String getAndRemove(Map<String, Object> args, String key) {
        Object value = args.get(key);
        args.remove(key);
        return value != null ? value.toString() : null;
    }
}
