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
package org.gradle.api.internal.project;

import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.configuration.ProjectEvaluator;

/**
 * Contains the singleton services which are shared by all builds.
 */
public class DefaultServiceRegistryFactory extends AbstractServiceRegistry implements ServiceRegistryFactory {
    public DefaultServiceRegistryFactory(RepositoryHandlerFactory repositoryHandlerFactory,
                                         ConfigurationContainerFactory configurationContainerFactory,
                                         DependencyFactory dependencyFactory, ProjectEvaluator projectEvaluator,
                                         ClassGenerator classGenerator) {
        add(RepositoryHandlerFactory.class, repositoryHandlerFactory);
        add(ConfigurationContainerFactory.class, configurationContainerFactory);
        add(DependencyFactory.class, dependencyFactory);
        add(ProjectEvaluator.class, projectEvaluator);
        add(PublishArtifactFactory.class, new DefaultPublishArtifactFactory());
        add(ITaskFactory.class, new AnnotationProcessingTaskFactory(new TaskFactory(classGenerator)));
        add(StandardOutputRedirector.class, new DefaultStandardOutputRedirector());
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }
}
