/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar

import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.plugins.sonar.model.SonarRootModel
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.util.GFileUtils
import org.sonar.batch.bootstrapper.Bootstrapper

import javax.inject.Inject

/**
 * Analyzes a project hierarchy and writes the results to the
 * Sonar database.
 *
 * @deprecated The 'sonar' plugin has been superseded by the official plugin from SonarQube, please see: http://docs.sonarqube.org/display/SONAR/Analyzing+with+Gradle
 */
@Deprecated
class SonarAnalyze extends ConventionTask {
    /**
     * Entry point to Sonar configuration.
     */
    SonarRootModel rootModel

    @Inject
    protected ModuleRegistry getModuleRegistry() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ClassLoaderFactory getClassLoaderFactory() {
        // Decoration takes care of the implementation
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void analyze() {
        GFileUtils.mkdirs(rootModel.bootstrapDir)
        def bootstrapper = new Bootstrapper("Gradle", rootModel.server.url, rootModel.bootstrapDir)

        def pluginClassLoaderAllowedPackages = ["groovy", "org.codehaus.groovy", "org.apache.log4j", "org.apache.commons.logging", "org.gradle.api.plugins.sonar.model"]

        def filteringPluginClassLoader = classLoaderFactory.createFilteringClassLoader(SonarAnalyze.classLoader)
        pluginClassLoaderAllowedPackages.each { filteringPluginClassLoader.allowPackage(it) }
        filteringPluginClassLoader.allowResource("logback.xml")
        def pluginAndLoggingClassLoader = new MutableURLClassLoader(filteringPluginClassLoader, getLogbackAndSlf4jUrls())

        def bootstrapperParentClassLoaderAllowedPackages = pluginClassLoaderAllowedPackages + ["org.slf4j", "ch.qos.logback"]
        def classLoader = bootstrapper.createClassLoader(getGradleSonarUrls() as URL[], pluginAndLoggingClassLoader, bootstrapperParentClassLoaderAllowedPackages as String[])

        def analyzerClass = classLoader.loadClass("org.gradle.api.plugins.sonar.internal.SonarCodeAnalyzer")
        def analyzer = analyzerClass.newInstance()
        analyzer.rootModel = rootModel
        analyzer.execute()
    }

    protected List<URL> getGradleSonarUrls() {
        moduleRegistry.getModule("gradle-sonar").implementationClasspath.asURLs
    }

    protected List<URL> getLogbackAndSlf4jUrls() {
        def moduleNames = ["logback-classic", "logback-core", "slf4j-api"]
        moduleNames.collectMany { moduleRegistry.getExternalModule(it).classpath.asURLs }
    }
}
