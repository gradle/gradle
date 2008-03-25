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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaPlugin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class DefaultSettings implements Settings {
    Logger logger = LoggerFactory.getLogger(DefaultSettings)
    static final String BUILD_CONFIGURATION = 'build'
    static final String DEFAULT_BUILD_SRC_DIR = 'buildSrc'

    File currentDir
    File rootDir
    DependencyManager dependencyManager
    BuildSourceBuilder buildSourceBuilder

    String buildSrcDir
    String buildSrcScriptName
    List buildSrcTaskNames
    boolean buildSrcRecursive
    boolean buildSrcSearchUpwards
    Map buildSrcProjectProperties
    Map buildSrcSystemProperties

    List projectPaths = []

    DefaultSettings() {}

    DefaultSettings(File currentDir, File rootDir, DependencyManagerFactory dependencyManagerFactory,
                    BuildSourceBuilder buildSourceBuilder, File gradleUserHomeDir) {
        this.currentDir = currentDir
        this.rootDir = rootDir
        this.dependencyManager = dependencyManagerFactory.createDependencyManager()
        
        configureDependencyManager(dependencyManager, gradleUserHomeDir)
        this.buildSourceBuilder = buildSourceBuilder
        dependencyManager.addConfiguration(BUILD_CONFIGURATION)
        dependencyManager.resolvers.add([name: 'Maven2Repo', url: 'http://repo1.maven.org/maven2/'])
        buildSrcDir = DEFAULT_BUILD_SRC_DIR
        buildSrcScriptName = Project.DEFAULT_PROJECT_FILE
        buildSrcTaskNames = [JavaPlugin.CLEAN, JavaPlugin.INSTALL_LIB]
        buildSrcRecursive = true
        buildSrcSearchUpwards = true
        buildSrcProjectProperties = [:]
        buildSrcSystemProperties = [:]
    }

    void include(String[] projectPaths) {
        this.projectPaths.addAll(projectPaths as List)
    }

    void addDependencies(Object[] dependencies) {
        dependencyManager.addDependencies([BUILD_CONFIGURATION], dependencies)
    }

    void setResolvers(List resolvers) {
        dependencyManager.resolvers = resolvers
    }

    List getResolvers() {
        dependencyManager.resolvers
    }

    URLClassLoader createClassLoader() {
        def dependency = null
        if (buildSourceBuilder) {
            dependency = buildSourceBuilder.createDependency(new File(rootDir, buildSrcDir), dependencyManager.buildResolverDir, buildSrcScriptName,
                    buildSrcTaskNames, buildSrcProjectProperties, buildSrcSystemProperties, buildSrcRecursive, buildSrcSearchUpwards)
        }
        logger.debug("Build src dependency: $dependency")
        if (dependency) {
            dependencyManager.addDependencies([BUILD_CONFIGURATION], dependency)
        } else {
            logger.info('No build sources found.')
        }
        URL[] classpath = dependencyManager.resolveClasspath(BUILD_CONFIGURATION).collect {File file ->
            Locator.fileToURL(file)
        }
        logger.debug("Adding to classpath: ${classpath as List}")
        ClassLoader parentClassLoader = Thread.currentThread().contextClassLoader
        new URLClassLoader(classpath, parentClassLoader)
    }

    private configureDependencyManager(DependencyManager dependencyManager, File gradleUserHomeDir) {
        assert gradleUserHomeDir
        DefaultProject dummyProjectForDepencencyManager = new DefaultProject()
        dummyProjectForDepencencyManager.group = 'org.gradle'
        dummyProjectForDepencencyManager.name = 'build'
        dummyProjectForDepencencyManager.version = 'SNAPSHOT'
        dummyProjectForDepencencyManager.gradleUserHome = gradleUserHomeDir.canonicalPath
        dependencyManager.project = dummyProjectForDepencencyManager
        dependencyManager
    }
}