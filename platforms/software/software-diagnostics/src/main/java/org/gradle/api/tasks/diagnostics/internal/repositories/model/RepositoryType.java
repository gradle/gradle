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

package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import org.jspecify.annotations.NullMarked;

/**
 * Categorization of a reported repository by its underlying Gradle implementation class.
 *
 * <p>The four standard types — {@link #MAVEN}, {@link #MAVEN_LOCAL}, {@link #IVY},
 * {@link #FLAT_DIR} — cover every repository produced by Gradle's built-in repository
 * factories. Third-party subclasses of {@code AbstractArtifactRepository} that do not
 * implement any of those interfaces are classified as {@link #CUSTOM}.
 */
@NullMarked
public enum RepositoryType {
    /** Any {@code MavenArtifactRepository} that is not a local Maven repository. */
    MAVEN,
    /** The {@code mavenLocal()} repository. */
    MAVEN_LOCAL,
    /** Any {@code IvyArtifactRepository}. */
    IVY,
    /** Any {@code FlatDirectoryArtifactRepository}. */
    FLAT_DIR,
    /** A third-party {@code AbstractArtifactRepository} subclass that matches none of the above. */
    CUSTOM
}
