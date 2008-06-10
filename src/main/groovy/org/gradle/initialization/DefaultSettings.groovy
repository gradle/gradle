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

import org.apache.tools.ant.launch.Locator
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


    FileSystemResolver createFlatDirResolver(String name, File[] dirs) {
        dependencyManager.createFlatDirResolver(name, dirs)
    }

    FileSystemResolver addFlatDirResolver(String name, File[] dirs) {
        dependencyManager.addFlatDirResolver(name, dirs)
    }

    DualResolver addMavenRepo(String[] jarRepoUrls) {
        dependencyManager.addMavenRepo(jarRepoUrls)
    }

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls) {
        dependencyManager.addMavenStyleRepo(name, root, jarRepoUrls)
    }

    URLClassLoader createClassLoader() {
        def dependency = null
        if (buildSourceBuilder) {
            dependency = buildSourceBuilder.createDependency(dependencyManager.buildResolverDir,
                    StartParameter.newInstance(buildSrcStartParameter, currentDir: new File(rootFinder.rootDir, DEFAULT_BUILD_SRC_DIR)))
        }
        logger.debug("Build src dependency: $dependency")
        if (dependency) {
            dependencyManager.dependencies([BUILD_CONFIGURATION], dependency)
        } else {
            logger.info('No build sources found.')
        }
        URL[] classpath = dependencyManager.resolve(BUILD_CONFIGURATION).collect {File file ->
            Locator.fileToURL(file)
        }
        logger.debug("Adding to classpath: ${classpath as List}")
        ClassLoader parentClassLoader = Thread.currentThread().contextClassLoader
        new URLClassLoader(classpath, parentClassLoader)
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