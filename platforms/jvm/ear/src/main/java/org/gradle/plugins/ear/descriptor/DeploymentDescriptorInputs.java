/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.ear.descriptor;

import org.gradle.api.Incubating;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of the {@link DeploymentDescriptor} of an EAR task.
 *
 * @see org.gradle.plugins.ear.EarInputs#getDeploymentDescriptor()
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface DeploymentDescriptorInputs {

    /**
     * The name of the descriptor file.
     *
     * @see DeploymentDescriptor#getFileName()
     * @since 10.0.0
     */
    Property<String> getFileName();

    /**
     * The version of the application schema.
     *
     * @see DeploymentDescriptor#getVersion()
     * @since 10.0.0
     */
    Property<String> getVersion();

    /**
     * The application name.
     *
     * @see DeploymentDescriptor#getApplicationName()
     * @since 10.0.0
     */
    Property<String> getApplicationName();

    /**
     * Whether modules are initialized in the order they are declared.
     *
     * @see DeploymentDescriptor#getInitializeInOrder()
     * @since 10.0.0
     */
    Property<Boolean> getInitializeInOrder();

    /**
     * The application description.
     *
     * @see DeploymentDescriptor#getDescription()
     * @since 10.0.0
     */
    Property<String> getDescription();

    /**
     * The application display name.
     *
     * @see DeploymentDescriptor#getDisplayName()
     * @since 10.0.0
     */
    Property<String> getDisplayName();

    /**
     * The name of the library directory.
     *
     * @see DeploymentDescriptor#getLibraryDirectory()
     * @since 10.0.0
     */
    Property<String> getLibraryDirectory();

    /**
     * The modules of the application.
     *
     * @see DeploymentDescriptor#getModules()
     * @since 10.0.0
     */
    SetProperty<EarModule> getModules();

    /**
     * The security roles of the application.
     *
     * @see DeploymentDescriptor#getSecurityRoles()
     * @since 10.0.0
     */
    SetProperty<EarSecurityRole> getSecurityRoles();

    /**
     * The module type of each module path.
     *
     * @see DeploymentDescriptor#getModuleTypeMappings()
     * @since 10.0.0
     */
    MapProperty<String, String> getModuleTypeMappings();
}
