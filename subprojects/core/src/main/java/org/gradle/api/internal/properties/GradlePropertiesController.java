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

package org.gradle.api.internal.properties;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Manages the lifecycle of {@link GradleProperties} for builds and projects within a build tree.
 * <p>
 * The controller does not load properties on-demand. Instead, the properties must be explicitly
 * loaded during the lifecycle of builds and projects.
 * <p>
 * Instances of {@code GradleProperties} can be obtained before the corresponding properties have loaded.
 * This is useful when the properties need to be injected as a service in the corresponding scope.
 * Accessing the values of the unloaded properties, however, will result in an exception.
 * <p>
 * After the build properties have been loaded, it is possible to {@link #unloadGradleProperties(BuildIdentifier) unload} them.
 * It is only possible to unload build properties before any project properties have been loaded.
 * The support for unloading is currently required by Configuration Cache, where properties are loaded before checking
 * the fingerprint and unloaded in case the fingerprint is rejected.
 */
@ServiceScope(Scope.BuildTree.class)
public interface GradlePropertiesController {

    /**
     * Returns build-scoped {@link GradleProperties} for the specified build.
     * <p>
     * Build-scoped properties are composed of multiple sources with the following priority:
     * <ul>
     * <li>{@code startParameter.projectProperties} aka {@code -P} arguments</li>
     * <li>System properties with {@code org.gradle.project.} prefix</li>
     * <li>Environment variables with {@code ORG_GRADLE_PROJECT_} prefix</li>
     * <li>{@code <Gradle User Home>/gradle.properties}</li>
     * <li>{@code <build-root directory>/gradle.properties}</li>
     * <li>{@code <Gradle Home>/gradle.properties}</li>
     * </ul>
     * <p>
     * The instance of {@code GradleProperties} can be obtained before the corresponding properties
     * have been {@link #loadGradleProperties(BuildIdentifier, File, boolean) loaded}.
     * However, accessing of property values will fail with an exception if the properties have not been loaded by then.
     */
    GradleProperties getGradleProperties(BuildIdentifier buildId);

    /**
     * Returns project-scoped {@link GradleProperties} for the specified project.
     * <p>
     * Project-scoped properties are composed of multiple sources with the following priority:
     * <ul>
     * <li>{@code startParameter.projectProperties} aka {@code -P} arguments</li>
     * <li>System properties with {@code org.gradle.project.} prefix</li>
     * <li>Environment variables with {@code ORG_GRADLE_PROJECT_} prefix</li>
     * <li>{@code <Gradle User Home>/gradle.properties}</li>
     * <li><strong>{@code <project directory>/gradle.properties}</strong></li>
     * <li>{@code <build-root directory>/gradle.properties}</li>
     * <li>{@code <Gradle Home>/gradle.properties}</li>
     * </ul>
     * <p>
     * Note that project-scoped properties only differ from build-scoped properties by also taking into account
     * the {@code gradle.properties} file in the project directory. But that file does not take the highest priority.
     * Also note that the {@code gradle.properties} of a <strong>parent project is not considered</strong>.
     * That is unless the parent project is the root project, and its {@code gradle.properties} can be the same file
     * as the one in the build root directory.
     * <p>
     * The instance of {@code GradleProperties} can be obtained before the corresponding properties
     * have been {@link #loadGradleProperties(BuildIdentifier, File, boolean) loaded}.
     * However, accessing of property values will fail with an exception if the properties have not been loaded by then.
     */
    GradleProperties getGradleProperties(ProjectIdentity projectId);

    /**
     * Returns the {@link GradleProperties} associated with the given scope.
     */
    GradleProperties getGradleProperties(GradlePropertyScope propertyScope);

    /**
     * Loads build-scoped {@link GradleProperties} from the specified build root directory.
     * <p>
     * See {@link #getGradleProperties(BuildIdentifier) build-scoped properties} on which sources are considered.
     * <p>
     * If {@code setSystemProperties} is true, properties with {@code systemProp.} prefix are set as system properties.
     * However, the <strong>system properties are only sourced from {@code gradle.properties} files</strong>:
     * <ul>
     * <li>{@code <Gradle User Home>/gradle.properties}</li>
     * <li>{@code <build-root directory>/gradle.properties}</li>
     * <li>{@code <Gradle Home>/gradle.properties}</li>
     * </ul>
     *
     * @param buildRootDir directory containing the {@code gradle.properties} file
     * @param setSystemProperties whether to set system properties from loaded properties
     */
    void loadGradleProperties(BuildIdentifier buildId, File buildRootDir, boolean setSystemProperties);

    /**
     * Unloads build-scoped properties.
     * <p>
     * Subsequent calls to {@link #loadGradleProperties(BuildIdentifier, File, boolean)} will
     * reload properties and re-evaluate system property assignments.
     * <p>
     * Build-scoped properties can't be unloaded if any {@link #loadGradleProperties(ProjectIdentity, File) project-scoped}
     * properties have been loaded.
     */
    void unloadGradleProperties(BuildIdentifier buildId);

    /**
     * Loads project-scoped {@link GradleProperties} from the specified project directory.
     * <p>
     * See {@link #getGradleProperties(ProjectIdentity) project-scoped properties} on which sources are considered.
     * <p>
     * Properties of the owner-build must have been loaded before loading project properties.
     *
     * @param projectDir directory containing the project's {@code gradle.properties} file
     */
    void loadGradleProperties(ProjectIdentity projectId, File projectDir);

    /**
     * Unloads all project-scoped and build-scoped properties.
     * <p>
     * Subsequent calls to {@link #loadGradleProperties(BuildIdentifier, File, boolean)} will
     * reload properties and re-evaluate system property assignments.
     */
    void unloadAll();

}
