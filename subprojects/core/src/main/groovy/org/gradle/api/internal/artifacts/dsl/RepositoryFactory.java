/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import java.util.Map;

/**
 * A repository factory is capable of creating different kinds of {@link org.gradle.api.artifacts.repositories.ArtifactRepository} types.
 */
public interface RepositoryFactory {

    /*
        Internal note: Not everything was copied from RepositoryHandler, only what wasn't going to be deprecated eventually.
     */

    /**
     * Creates a repository that looks into a number of directories for artifacts. The artifacts are expected to be located in the
     * root of the specified directories. The repository ignores any group/organization information specified in the
     * dependency section of your build script. If you only use this kind of repository you might specify your
     * dependencies like <code>":junit:4.4"</code> instead of <code>"junit:junit:4.4"</code>.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository.
     * The default is a Hash value of the rootdir paths. The name is used in the console output,
     * to point to information related to a particular repository. A name must be unique amongst a repository group.</td></tr>
     * <tr><td><code>dirs</code></td>
     *     <td>Specifies a list of rootDirs where to look for dependencies. These are evaluated as for {@link org.gradle.api.Project#files(Object...)}</td></tr>
     * </table>
     *
     * <p>Examples:
     * <pre>
     * repositories {
     *     flatDir name: 'libs', dirs: "$projectDir/libs"
     *     flatDir dirs: ["$projectDir/libs1", "$projectDir/libs2"]
     * }
     * </pre>
     * </p>
     *
     * @param args The arguments used to configure the repository.
     * @return the created repository
     * @throws org.gradle.api.InvalidUserDataException In the case neither rootDir nor rootDirs is specified of if both
     * are specified.
     */
    FlatDirectoryArtifactRepository flatDir(Map<String, ?> args);

    /**
     * Creates and configures a repository which will look for dependencies in a number of local directories.
     *
     * @param action The action to execute to configure the repository.
     * @return The repository.
     */
    FlatDirectoryArtifactRepository flatDir(Action<? super FlatDirectoryArtifactRepository> action);

    /**
     * Creates a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#MAVEN_CENTRAL_URL}. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_CENTRAL_REPO_NAME}.
     *
     * @return the created repository
     */
    MavenArtifactRepository mavenCentral();

    /**
     * Creates a repository which looks in the local Maven cache for dependencies. The name of the repository is
     * {@value org.gradle.api.artifacts.ArtifactRepositoryContainer#DEFAULT_MAVEN_LOCAL_REPO_NAME}.
     *
     * @return the created repository
     */
    MavenArtifactRepository mavenLocal();

    /**
     * Creates and configures a Maven repository.
     *
     * @param action The action to use to configure the repository.
     * @return The created repository.
     */
    MavenArtifactRepository maven(Action<? super MavenArtifactRepository> action);

    /**
     * Creates and configures an Ivy repository.
     *
     * @param action The action to use to configure the repository.
     * @return The created repository.
     */
    IvyArtifactRepository ivy(Action<? super IvyArtifactRepository> action);

}
