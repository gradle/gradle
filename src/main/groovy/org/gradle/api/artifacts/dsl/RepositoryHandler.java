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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.util.Map;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public interface RepositoryHandler extends ResolverContainer {
    final String DEFAULT_MAVEN_DEPLOYER_NAME = "maven-deployer";
    final String DEFAULT_MAVEN_INSTALLER_NAME = "maven-installer";

    /**
     * Adds a resolver that looks into a number of directories for artifacts. The artifacts are expected to be located in the
     * root of the specified directories. The resolver ignores any group/organization information specified in the
     * dependency section of your build script. If you only use this kind of resolver you might specify your
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
     * <tr><td><code>rootDir</code></td>
     *     <td>Specifies a single rootDir where to look for dependencies.</td></tr>
     * <tr><td><code>rootDirs</code></td>
     *     <td>Specifies a list of rootDirs where to look for dependencies.</td></tr>
     * </table>
     *
     * <p>Examples:
     * <pre>
     * repositories {
     *     flatDir name: 'libs', rootDir: "$projectDir/libs"
     *     flatDir(rootDirs: ["$projectDir/libs1", "$projectDir/libs2"])
     * }
     * </pre>
     * </p>
     *
     * @param args
     * @return the added resolver
     * @throws org.gradle.api.InvalidUserDataException In the case neither rootDir nor rootDirs is specified of if both
     * are specified.
     */
    FileSystemResolver flatDir(Map args);

    /**
     * Adds a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * always {@link #MAVEN_CENTRAL_URL}. The behavior of this resolver
     * is otherwise the same as the ones added by {@link #mavenRepo(java.util.Map)}.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository. The default is {@link #DEFAULT_MAVEN_CENTRAL_REPO_NAME}
     * is used as the name. A name must be unique amongst a repository group.
     * </td></tr>
     * <tr><td><code>jarOnlyRepos</code></td>
     *     <td>A list of jar repositories. Sometimes the artifact
     * lives in a different repository than the pom. In such a case you can specify further locations to look for an artifact.
     * But be aware that the pom is only looked up in the root repository</td></tr>
     * </table>
     *
     * <p>Examples:
     * <pre>
     * repositories {
     *     mavenCentral jarOnlyRepos: ["http://www.mycompany.com/repository1", "http://www.mycompany.com/repository2"]
     *     mavenCentral(name: "nonDefaultName", jarOnlyRepos: ["http://www.mycompany.com/repository"])
     * }
     * </pre>
     * </p>
     *
     * @param args A list of urls of repositories to look for artifacts only.
     * @return the added resolver
     * @see #mavenRepo(java.util.Map)
     */
    DependencyResolver mavenCentral(Map args);

    /**
     * Adds a repository which looks in the Maven central repository for dependencies. The URL used to access this repository is
     * {@link #MAVEN_CENTRAL_URL}. The name of the repository is {@link #DEFAULT_MAVEN_CENTRAL_REPO_NAME}.
     *
     * <p>Examples:
     * <pre>
     * repositories {
     *     mavenCentral()
     * }
     * </pre>
     * </p>
     *
     * @return the added resolver
     * @see #mavenRepo(java.util.Map)
     * @see #mavenCentral(java.util.Map)
     */
    DependencyResolver mavenCentral();

    /**
     * Adds a repository which is Maven compatible. The compatibility is in regard to layout, snapshothandling and
     * dealing with the pom.xml. This repository can't be used for publishing in a Maven compatible way. For publishing
     * to a Maven repository, have a look at {@link #mavenDeployer(java.util.Map)} or {@link #mavenInstaller(java.util.Map)}.
     *
     * By default the repository accepts to resolve artifacts without a pom. The repository always looks first for the pom
     * in the root repository. It then looks for the artifact in the root repository. Sometimes the artifact
     * lives in a different repository than the pom. In such a case you can specify further locations to look for an artifact.
     * But be aware that the pom is only looked up in the root repository.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository. The default is the URL of the root repo.
     * The name is used in the console output,
     * to point to information related to a particular repository. A name must be unique amongst a repository group.
     * </td></tr>
     * <tr><td><code>rootRepo</code></td>
     *     <td>The url of the root repo (the toString value of the value is used). Gradle always looks first for the pom
     * in the root repository. After this it looks for the artifact in the root repository and if it can't be found,
     * in jarOnlyRepos.</td></tr>
     * <tr><td><code>jarOnlyRepos</code></td>
     *     <td>A list of jar repository url's. Sometimes the artifact
     * lives in a different repository than the pom. In such a case you can specify further locations to look for an artifact.
     * But be aware that the pom is only looked up in the root repository</td></tr>
     * </table>
     *
     * <p>Examples:
     * <pre>
     * repositories {
     *     mavenRepo jarOnlyRepos: ["http://www.mycompany.com/repository1", "http://www.mycompany.com/repository2"]
     *     mavenRepo(name: "nonDefaultName", jarOnlyRepos: ["http://www.mycompany.com/repository"])
     * }
     * </pre>
     * </p>
     *
     * For Ivy related reasons, Maven Snapshot dependencies are only properly resolved if no additional jar locations
     * are specified. This is unfortunate and we hope to improve this in a future release.
     *
     * @param args The argument to create the repository
     * @return the added repository
     * @see #mavenCentral(java.util.Map)
     */
    DependencyResolver mavenRepo(Map args);

    /**
     * Adds a repository for publishing to a Maven repository. This repository can not be used for reading from a Maven
     * repository.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository. The default is <em>maven-deployer-{SOME_ID}</em>.
     * The name is used in the console output,
     * to point to information related to a particular repository. A name must be unique amongst a repository group.
     * </td></tr>
     * </table>
     * 
     * @param args The argument to create the repository
     * @return The added repository
     * @see #mavenDeployer(java.util.Map, groovy.lang.Closure)
     */
    GroovyMavenDeployer mavenDeployer(Map args);

    /**
     * Behaves the same way as {@link #mavenDeployer(java.util.Map)}. Additionally a closure can be passed to
     * further configure the added repository.
     *  
     * @param args The argument to create the repository
     * @param configureClosure
     * @return The added repository
     */
    GroovyMavenDeployer mavenDeployer(Map args, Closure configureClosure);

    /**
     * Adds a repository for installing to a local Maven cache. This repository can not be used for reading.
     *
     * The following parameter are accepted as keys for the map:
     *
     * <table summary="Shows property keys and associated values">
     * <tr><th>Key</th>
     *     <th>Description of Associated Value</th></tr>
     * <tr><td><code>name</code></td>
     *     <td><em>(optional)</em> The name of the repository. The default is <em>maven-installer-{SOME_ID}</em>.
     * The name is used in the console output,
     * to point to information related to a particular repository. A name must be unique amongst a repository group.
     * </td></tr>
     * </table>
     *
     * @param args The argument to create the repository
     * @return The added repository
     * @see #mavenInstaller(java.util.Map, groovy.lang.Closure) (java.util.Map, groovy.lang.Closure)
     */
    MavenResolver mavenInstaller(Map args);

    /**
     * Behaves the same way as {@link #mavenInstaller(java.util.Map)}. Additionally a closure can be passed to
     * further configure the added repository.
     *
     * @param args The argument to create the repository
     * @param configureClosure
     * @return The added repository
     */
    MavenResolver mavenInstaller(Map args, Closure configureClosure);

}
