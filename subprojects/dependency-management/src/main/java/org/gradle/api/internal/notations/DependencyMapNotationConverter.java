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
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.MapKey;
import org.gradle.internal.typeconversion.MapNotationConverter;

import javax.annotation.Nullable;

public class DependencyMapNotationConverter<T> extends MapNotationConverter<T> {

    private final Instantiator instantiator;
    private final Class<T> resultingType;

    public DependencyMapNotationConverter(Instantiator instantiator, Class<T> resultingType) {
        this.instantiator = instantiator;
        this.resultingType = resultingType;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("Maps").example("[group: 'org.gradle', name: 'gradle-core', version: '1.0']");
    }

    protected T parseMap(@MapKey("group") @Nullable String group,
                         @MapKey("name") @Nullable String name,
                         @MapKey("version") @Nullable String version,
                         @MapKey("configuration") @Nullable String configuration,
                         @MapKey("ext") @Nullable String ext,
                         @MapKey("classifier") @Nullable String classifier) {
        T dependency;
        if (configuration == null) {
            dependency = instantiator.newInstance(resultingType, group, name, version);
        } else {
            dependency = instantiator.newInstance(resultingType, group, name, version, configuration);
        }
        if (dependency instanceof ExternalDependency) {
            ModuleFactoryHelper.addExplicitArtifactsIfDefined((ExternalDependency) dependency, ext, classifier);
        }
        return dependency;
    }

}
