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
package org.gradle.api.artifacts;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.Project;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.internal.IConventionAware;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public interface ResolverContainer extends IConventionAware {
    String DEFAULT_MAVEN_CENTRAL_REPO_NAME = "MavenRepo";
    String MAVEN_CENTRAL_URL = "http://repo1.maven.org/maven2/";
    String MAVEN_REPO_PATTERN
            = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]";
    String FLAT_DIR_RESOLVER_PATTERN = "[artifact](-[revision])(-[classifier]).[ext]";
    String DEFAULT_CACHE_ARTIFACT_PATTERN
            = "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])";
    String DEFAULT_CACHE_IVY_PATTERN = "[organisation]/[module](/[branch])/ivy-[revision].xml";
    String BUILD_RESOLVER_PATTERN = "[organisation]/[module]/[revision]/[type]s/[artifact].[ext]";
    String DEFAULT_CACHE_NAME = "default-gradle-cache";
    String BUILD_RESOLVER_NAME = "build-resolver";
    String DEFAULT_CACHE_DIR_NAME = "cache";
    String TMP_CACHE_DIR_NAME = Project.TMP_DIR_NAME + "/tmpIvyCache";

    DependencyResolver add(Object userDescription);

    DependencyResolver add(Object userDescription, Closure configureClosure);

    DependencyResolver resolver(String name);

    List<DependencyResolver> getResolverList();

    FileSystemResolver createFlatDirResolver(String name, Object... dirs);

    AbstractResolver createMavenRepoResolver(String name, String root, String[] jarRepoUrls);

    List<String> getResolverNames();

    Map<String, DependencyResolver> getResolvers();

    GroovyMavenDeployer addMavenDeployer(String name);

    GroovyMavenDeployer addMavenDeployer(String name, Closure configureClosure);

    MavenResolver addMavenInstaller(String name);

    MavenResolver addMavenInstaller(String name, Closure configureClosure);

/**
     * Adds a resolver that look in a list of directories for artifacts. The artifacts are expected to be located in the
     * root of the specified directories. The resolver ignores any group/organization information specified in the
     * dependency section of your build script. If you only use this kind of resolver you might specify your
     * dependencies like <code>":junit:4.4"</code> instead of <code>"junit:junit:4.4"</code>
     *
     * @param name The name of the resolver
     * @param dirs The directories to look for artifacts.
     * @return the added resolver
     */
    FileSystemResolver flatDir(String name, Object... dirs);

    /**
     * Adds a resolver which look in the official Maven Repo for dependencies. The URL of the official Repo is {@link
     * #MAVEN_CENTRAL_URL}. The name is {@link #DEFAULT_MAVEN_CENTRAL_REPO_NAME}. The behavior of this resolver is otherwise the
     * same as the ones added by {@link #mavenRepo(String, String, String[])}.
     *
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only.
     * @return the added resolver
     * @see #mavenRepo (String, String, String[])
     */
    DependencyResolver mavenCentral(String... jarRepoUrls);

    /**
     * Adds a resolver that uses Maven pom.xml descriptor files for resolving dependencies. By default the resolver
     * accepts to resolve artifacts without a pom. The resolver always looks first in the root location for the pom and
     * the artifact. Sometimes the artifact is supposed to live in a different repository as the pom. In such a case you
     * can specify further locations to look for an artifact. But be aware that the pom is only looked for in the root
     * location.
     *
     * For Ivy related reasons, Maven Snapshot dependencies are only properly resolved if no additional jar locations
     * are specified. This is unfortunate and we hope to improve this in our next release.
     *
     * @param name The name of the resolver
     * @param root A URL to look for artifacts and pom's
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only.
     * @return the added resolver
     */
    DependencyResolver mavenRepo(String name, String root, String... jarRepoUrls);

    void setMavenPomDir(File mavenPomDir);

    Conf2ScopeMappingContainer getMavenScopeMappings();

    File getMavenPomDir();

    FileSystemResolver flatDir(Object... dirs);

    DependencyResolver mavenRepo(String root, String... jarRepoUrls);
}
