/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.attributes.CompileView;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * APIs that are available for {@code dependencies} blocks used by JVM components.
 *
 * @apiNote This interface is intended to be used to mix-in methods that expose JVM-ecosystem specific dependencies to the DSL.
 * @implSpec The default implementation of all methods should not be overridden.
 *
 * @since 8.0
 */
@Incubating
public interface JvmDependencies extends Dependencies {
    /**
     * Injected service to create named objects.
     *
     * @return injected service
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     */
    @Inject
    ObjectFactory getObjectFactory();

    /**
     * Create a dependency on the current project's internal view. During compile-time, this dependency will
     * resolve the current project's implementation in addition to its API. During runtime, this dependency
     * behaves as a usual project dependency.
     *
     * @return the current project, including implementation details, as a dependency
     */
    default ProjectDependency projectInternalView() {
        ProjectDependency currentProject = project();
        currentProject.attributes(attrs -> {
            attrs.attribute(CompileView.VIEW_ATTRIBUTE, getObjectFactory().named(CompileView.class, CompileView.JAVA_INTERNAL));
        });
        return currentProject;
    }
}
