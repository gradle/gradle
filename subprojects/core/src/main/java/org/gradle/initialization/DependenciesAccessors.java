/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.internal.catalog.ExternalModuleDependencyFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;
import java.util.Map;

@ServiceScope(Scope.Build.class)
public interface DependenciesAccessors {
    void generateAccessors(List<VersionCatalogBuilder> builders, ClassLoaderScope classLoaderScope, Settings settings);
    void createExtensions(ProjectInternal project);
    Map<String, ExternalModuleDependencyFactory> createPluginsBlockFactories(ObjectFactory objects);
    ClassPath getSources();
    ClassPath getClasses();

    String IN_PLUGINS_BLOCK_FACTORIES_SUFFIX = "InPluginsBlock";
}
