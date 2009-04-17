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
    
    List<String> getResolverNames();

    Map<String, DependencyResolver> getResolvers();

    GroovyMavenDeployer addMavenDeployer(String name);

    GroovyMavenDeployer addMavenDeployer(String name, Closure configureClosure);

    MavenResolver addMavenInstaller(String name);

    MavenResolver addMavenInstaller(String name, Closure configureClosure);

    void setMavenPomDir(File mavenPomDir);

    Conf2ScopeMappingContainer getMavenScopeMappings();

    File getMavenPomDir();
}
