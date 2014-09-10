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
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationParser;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.reflect.Instantiator;

import java.util.Collection;

public class DependencyMapNotationParser<T extends ExternalDependency> extends MapNotationParser<T> {

    private final Instantiator instantiator;
    private final Class<T> resultingType;

    public DependencyMapNotationParser(Instantiator instantiator, Class<T> resultingType) {
        this.instantiator = instantiator;
        this.resultingType = resultingType;
    }

    @Override
    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("Maps, e.g. [group: 'org.gradle', name: 'gradle-core', version: '1.0'].");
    }

    protected T parseMap(@MapKey("group") @Optional String group,
                         @MapKey("name") @Optional String name,
                         @MapKey("version") @Optional String version,
                         @MapKey("configuration") @Optional String configuration,
                         @MapKey("ext") @Optional String ext,
                         @MapKey("classifier") @Optional String classifier) {
        T dependency = instantiator.newInstance(resultingType, group, name, version, configuration);
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dependency, ext, classifier);
        return dependency;
    }

}
