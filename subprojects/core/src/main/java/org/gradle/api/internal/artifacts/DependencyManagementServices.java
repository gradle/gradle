/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Factory for various types related to dependency management.
 */
@ServiceScope(Scope.Build.class)
public interface DependencyManagementServices {

    /**
     * Registers the dependency management DSL services.
     */
    void addDslServices(ServiceRegistration registration, DomainObjectContext domainObjectContext);

    /**
     * Create a dependency resolution instance detached from any project instance. As such, the instance
     * does not have a unique module identity. Resolutions made by this instance are performed from
     * an anonymous root component, which does not expose any consumable variants. As such, creating
     * configurations in a detached resolver is forbidden. Resolutions should only be performed using
     * detached configurations.
     * <p>
     * This resolver cannot resolve user-declared project dependencies, though is still able to resolve
     * project dependencies if they're substituted for a module dependency.
     *
     * @return A new dependency resolution instance. This instance comes with its own configuration container,
     * repositories, attribute schema, and other resolution services.
     */
    DependencyResolutionServices newDetachedResolver(DomainObjectContext owner);

    /**
     * Similar to {@link #newDetachedResolver(DomainObjectContext)}, but allows creating consumable configurations.
     * <p>
     * Creating any configuration in a buildscript is deprecated, however, we must allow creating consumable
     * configurations for backwards compatibility. Migrate usages of this method to
     * {@link #newDetachedResolver(DomainObjectContext)} in Gradle 9.0.
     */
    DependencyResolutionServices newBuildscriptResolver(DomainObjectContext owner);

    /**
     * Similar to {@link #newDetachedResolver(DomainObjectContext)}, but allows specifying
     * how file are resolved.
     */
    DependencyResolutionServices newDetachedResolver(
        FileResolver resolver,
        FileCollectionFactory fileCollectionFactory,
        DomainObjectContext owner
    );

    /**
     * Similar to {@link #newDetachedResolver(FileResolver, FileCollectionFactory, DomainObjectContext)},
     * but uses the identity of the provided project as the root component identity.
     * <p>
     * <strong>This method should be avoided and we should work to migrate away from this method</strong>.
     * This method is used to maintain backwards compatibility of project buildscripts, allowing them
     * to identify as the project. Project buildscript components are not the project, and should not
     * be identified as such, as it causes the resolution engine to resolve dependencies on the current
     * project to the buildscript component.
     */
    DependencyResolutionServices newProjectBuildscriptResolver(
        FileResolver resolver,
        FileCollectionFactory fileCollectionFactory,
        ProjectInternal project
    );
}
