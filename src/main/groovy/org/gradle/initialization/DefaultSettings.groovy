/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.initialization

import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.Project
import org.gradle.api.Settings
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.DualResolver
import org.gradle.StartParameter
import org.gradle.util.ClasspathUtil
import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class DefaultSettings implements Settings {
    private static Logger logger = LoggerFactory.getLogger(DefaultSettings)
    static final String BUILD_CONFIGURATION = 'build'
    static final String DEFAULT_BUILD_SRC_DIR = 'buildSrc'

    DependencyManager dependencyManager
    BuildSourceBuilder buildSourceBuilder

    Map additionalProperties = [:]

    StartParameter startParameter

    RootFinder rootFinder

    StartParameter buildSrcStartParameter

    List projectPaths = []

    DefaultSettings() {}

    DefaultSettings(DependencyManagerFactory dependencyManagerFactory,
                    BuildSourceBuilder buildSourceBuilder, RootFinder rootFinder, StartParameter startParameter) {
        this.rootFinder = rootFinder
        this.startParameter = startParameter
        this.dependencyManager = dependencyManagerFactory.createDependencyManager()

        configureDependencyManager(dependencyManager)
        this.buildSourceBuilder = buildSourceBuilder
        dependencyManager.addConfiguration(BUILD_CONFIGURATION)
        buildSrcStartParameter = new StartParameter(
                buildFileName: Project.DEFAULT_PROJECT_FILE,
                taskNames: [JavaPlugin.CLEAN, JavaPlugin.UPLOAD_LIBS],
                recursive: true,
                searchUpwards: true,
                gradleUserHomeDir: startParameter.gradleUserHomeDir
        )
    }

    void include(String[] projectPaths) {
        this.projectPaths.addAll(projectPaths as List)
    }

    void dependencies(Object[] dependencies) {
        dependencyManager.dependencies([BUILD_CONFIGURATION], dependencies)
    }

    void dependency(String id, Closure configureClosure = null) {
        dependencyManager.dependency([BUILD_CONFIGURATION], id, configureClosure)
    }

    void clientModule(String id, Closure configureClosure = null) {
        dependencyManager.clientModule([BUILD_CONFIGURATION], id, configureClosure)
    }

    ResolverContainer getResolvers() {
        dependencyManager.classpathResolvers
    }


    FileSystemResolver createFlatDirResolver(String name, Object[] dirs) {
        dependencyManager.createFlatDirResolver(name, dirs)
    }

    FileSystemResolver addFlatDirResolver(String name, Object[] dirs) {
        dependencyManager.addFlatDirResolver(name, dirs)
    }

    DualResolver addMavenRepo(String[] jarRepoUrls) {
        dependencyManager.addMavenRepo(jarRepoUrls)
    }

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls) {
        dependencyManager.addMavenStyleRepo(name, root, jarRepoUrls)
    }

    // todo We don't have command query separation here. This si a temporary thing. If our new classloader handling works out, which
    // adds simply the build script jars to the context classloader we can remove the return argument and simplify our design. 
    URLClassLoader createClassLoader() {
        URLClassLoader classLoader = Thread.currentThread().contextClassLoader
        def dependency = null
        StartParameter startParameter = StartParameter.newInstance(buildSrcStartParameter)
        startParameter.setCurrentDir(new File(rootFinder.rootDir, DEFAULT_BUILD_SRC_DIR)) 
        if (buildSourceBuilder) {
            dependency = buildSourceBuilder.createDependency(dependencyManager.buildResolverDir,
                    startParameter)
        }
        logger.debug("Build src dependency: $dependency")
        if (dependency) {
            dependencyManager.dependencies([BUILD_CONFIGURATION], dependency)
        } else {
            logger.info('No build sources found.')
        }
        List additionalClasspath = dependencyManager.resolve(BUILD_CONFIGURATION)
        File toolsJar = GradleUtil.getToolsJar()
        if (toolsJar) { additionalClasspath.add(toolsJar) }
        logger.debug("Adding to classpath: " + additionalClasspath)
        ClasspathUtil.addUrl(classLoader, additionalClasspath)
        classLoader
    }

    private configureDependencyManager(DependencyManager dependencyManager) {
        DefaultProject dummyProjectForDepencencyManager = new DefaultProject()
        dummyProjectForDepencencyManager.group = 'org.gradle'
        dummyProjectForDepencencyManager.name = 'build'
        dummyProjectForDepencencyManager.version = 'SNAPSHOT'
        dummyProjectForDepencencyManager.gradleUserHome = startParameter.gradleUserHomeDir.canonicalPath
        dependencyManager.project = dummyProjectForDepencencyManager
        dependencyManager
    }

    def propertyMissing(String property) {
        def delegateObject = [rootFinder, startParameter].find {
            it.metaClass.hasProperty(it, property)
        }
        if (delegateObject) { return delegateObject."$property" }
        if (rootFinder.gradleProperties.keySet().contains(property)) {
            return rootFinder.gradleProperties[property]
        }
        throw new MissingPropertyException(property, DefaultSettings)
    }
}