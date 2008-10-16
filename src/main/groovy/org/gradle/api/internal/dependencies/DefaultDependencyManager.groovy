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

package org.gradle.api.internal.dependencies

import org.gradle.api.DependencyManager
import org.gradle.api.dependencies.ExcludeRuleContainer
import org.gradle.util.WrapUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.dependencies.MavenPomGenerator

/**
 * @author Hans Dockter
 */
public class DefaultDependencyManager extends BaseDependencyManager implements DependencyManager {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager.class);

    public DefaultDependencyManager() {
        super();
    }

    public DefaultDependencyManager(IIvyFactory ivyFactory, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                             SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                             IDependencyResolver dependencyResolver, IDependencyPublisher dependencyPublisher, MavenPomGenerator mavenPomGenerator,
                             File buildResolverDir, ExcludeRuleContainer excludeRuleContainer) {
        super(ivyFactory, dependencyFactory, artifactFactory, settingsConverter, moduleDescriptorConverter,
                dependencyResolver, dependencyPublisher, mavenPomGenerator, buildResolverDir, excludeRuleContainer);
    }

    public def propertyMissing(String name) {
        if (configurations.get(name) != null) {
            return configuration(name)
        }
        throw new MissingPropertyException("$name is unknown property!")
    }
    
    public def methodMissing(String name, args) {
        if (configurations.get(name) == null) {
            if (!getMetaClass().respondsTo(this, name, args.size())) {
                throw new MissingMethodException(name, this.getClass(), args);
            }
            return getMetaClass().invokeMethod(this, name, args);
        }
        dependencies(WrapUtil.toList(name), args);
    }
}

