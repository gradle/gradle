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

import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.api.internal.notations.parsers.TypedNotationParser;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DependencyMapNotationParser<T extends ExternalDependency> extends TypedNotationParser<Map, T> {

    private final Instantiator instantiator;
    private final Class<T> resultingType;

    public DependencyMapNotationParser(Instantiator instantiator, Class<T> resultingType) {
        super(Map.class);
        this.instantiator = instantiator;
        this.resultingType = resultingType;
    }

    @Override
    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Maps, e.g. [group: 'org.gradle', name:'gradle-core', version: '1.0'].");
    }

    @Override
    public T parseType(Map notation) {
        Map<String, Object> args = new HashMap<String, Object>(notation);
        String group = getAndRemove(args, "group");
        String name = getAndRemove(args, "name");
        String version = getAndRemove(args, "version");
        String configuration = getAndRemove(args, "configuration");
        T dependency = instantiator.newInstance(resultingType, group, name, version, configuration);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency, getAndRemove(args, "ext"), getAndRemove(args,
                "classifier"));
        ConfigureUtil.configureByMap(args, dependency);
        return dependency;
    }

    private String getAndRemove(Map<String, Object> args, String key) {
        Object value = args.get(key);
        args.remove(key);
        return value != null ? value.toString() : null;
    }
}
