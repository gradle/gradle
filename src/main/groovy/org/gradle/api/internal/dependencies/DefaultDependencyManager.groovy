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
import org.gradle.api.Project
import org.gradle.api.dependencies.Configuration
import org.gradle.api.dependencies.DependencyContainer
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.internal.dependencies.ivy.BuildResolverHandler
import org.gradle.api.internal.dependencies.ivy.IvyService
import org.gradle.api.internal.dependencies.ivy.ResolverFactory
import org.gradle.util.WrapUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * @author Hans Dockter
 */
public class DefaultDependencyManager extends BaseDependencyManager implements DependencyManager {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager.class);

    public DefaultDependencyManager() {
        super();
    }

    public DefaultDependencyManager(Project project, DependencyContainer dependencyContainer, ArtifactContainer artifactContainer,
                                    ConfigurationContainer configurationContainer, ConfigurationResolverFactory configurationResolverFactory,
                                    ResolverContainer classpathResolvers, ResolverFactory resolverFactory, BuildResolverHandler buildResolverHandler,
                                    IvyService ivyHandler) {
        super(project, dependencyContainer, artifactContainer, configurationContainer, configurationResolverFactory, classpathResolvers,
                resolverFactory, buildResolverHandler, ivyHandler);
    }

    public def propertyMissing(String name) {
        Configuration configuration = findConfiguration(name)
        if (configuration != null) {
            return configuration
        }
        throw new MissingPropertyException("Property '$name' not found for DependencyManager for ${project}.")
    }

    public def methodMissing(String name, args) {
        if (findConfiguration(name) == null) {
            if (!getMetaClass().respondsTo(this, name, args.size())) {
                throw new MissingMethodException(name, this.getClass(), args);
            }
            return getMetaClass().invokeMethod(this, name, args);
        }
        if (args.length == 1 && args[0] instanceof Closure) {
            return configuration(name, args[0])
        }
        if (args.length == 2 && args[1] instanceof Closure) {
            return dependency(WrapUtil.toList(name), args[0], (Closure) args[1])
        }
        dependencies(WrapUtil.toList(name), args);
    }
}

