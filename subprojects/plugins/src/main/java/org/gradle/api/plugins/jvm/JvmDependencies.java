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
 * @apiNote
 * This API is likely to change until it's made stable.
 *
 * @implSpec These methods are not intended to be implemented by end users or plugin authors.
 * @since 8.0
 */
@Incubating
public interface JvmDependencies extends Dependencies {
    /**
     * Injected service to create named objects.
     *
     * @return injected service
     */
    @Inject
    ObjectFactory getObjectFactory();

    /**
     * Create a dependency on the current project's internal view. During compile-time, this dependency will
     * resolve the current project's implementation in addition to its API. During runtime, this dependency
     * behaves as a usual project dependency.
     *
     * @return the current project, including implementation details, as a dependency
     *
     * @since 8.0
     */
    default ProjectDependency projectInternalView() {
        ProjectDependency currentProject = project();
        currentProject.attributes(attrs -> {
            attrs.attribute(CompileView.VIEW_ATTRIBUTE, getObjectFactory().named(CompileView.class, CompileView.JAVA_INTERNAL));
        });
        return currentProject;
    }
}
