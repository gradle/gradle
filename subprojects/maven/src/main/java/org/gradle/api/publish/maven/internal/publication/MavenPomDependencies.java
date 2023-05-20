/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;

import java.util.Set;

/**
 * The dependencies of a Maven POM.
 *
 * <p>TODO: Currently this represents a POM's dependencies in a "Gradle" point of view.
 * To better align this interface with the way Maven represents dependencies in a POM,
 * we should add a {@code scope} and {@code optional} flag to a {@link MavenDependency}
 * so that we can represent these dependencies as Maven would. Then, we can combine all
 * normal dependencies into a single list and do the same for dependency management
 * dependencies.<p>
 */
public interface MavenPomDependencies {

    Set<MavenDependencyInternal> getRuntimeDependencies();

    Set<MavenDependencyInternal> getApiDependencies();

    Set<MavenDependencyInternal> getOptionalApiDependencies();

    Set<MavenDependencyInternal> getOptionalRuntimeDependencies();

    Set<MavenDependencyInternal> getRuntimeDependencyManagement();

    Set<MavenDependencyInternal> getApiDependencyManagement();

    Set<MavenDependencyInternal> getImportDependencyManagement();

}
