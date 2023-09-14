/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.initialization

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

class DefaultScriptHandlerTest extends Specification {
    def repositoryHandler = Mock(RepositoryHandler)
    def dependencyHandler = Mock(DependencyHandler)
    def configurationContainer = Mock(RoleBasedConfigurationContainerInternal)
    def configuration = Mock(ResettableConfiguration)
    def scriptSource = Stub(ScriptSource)
    def depMgmtServices = Mock(DependencyResolutionServices) {
        getAttributesSchema() >> Stub(AttributesSchemaInternal)
    }
    def baseClassLoader = new ClassLoader() {}
    def classLoaderScope = Stub(ClassLoaderScope) {
        getLocalClassLoader() >> baseClassLoader
    }
    def classpathResolver = Mock(ScriptClassPathResolver)
    def handler = new DefaultScriptHandler(scriptSource, depMgmtServices, classLoaderScope, classpathResolver)

    def "adds classpath configuration when configuration container is queried"() {
        when:
        handler.configurations
        handler.configurations

        then:
        1 * depMgmtServices.configurationContainer >> configurationContainer
        1 * depMgmtServices.dependencyHandler >> dependencyHandler
        1 * configurationContainer.migratingUnlocked('classpath', ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE) >> configuration
        1 * classpathResolver.prepareClassPath(configuration, dependencyHandler)
        0 * configurationContainer._
        0 * depMgmtServices._
    }

    def "adds classpath configuration when dependencies container is queried"() {
        when:
        handler.dependencies
        handler.dependencies

        then:
        1 * depMgmtServices.configurationContainer >> configurationContainer
        1 * depMgmtServices.dependencyHandler >> dependencyHandler
        1 * configurationContainer.migratingUnlocked('classpath', ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE) >> configuration
        1 * classpathResolver.prepareClassPath(configuration, dependencyHandler)
        0 * configurationContainer._
        0 * depMgmtServices._
    }

    def "does not resolve classpath configuration when configuration container has not been queried"() {
        when:
        def classpath = handler.instrumentedScriptClassPath

        then:
        0 * configuration._
        1 * classpathResolver.resolveClassPath(null) >> ClassPath.EMPTY

        and:
        classpath == ClassPath.EMPTY
    }

    def "resolves classpath configuration when configuration container has been queried"() {
        def classpath = Mock(ClassPath)

        when:
        handler.configurations
        def result = handler.instrumentedScriptClassPath

        then:
        result == classpath

        and:
        1 * depMgmtServices.configurationContainer >> configurationContainer
        1 * depMgmtServices.dependencyHandler >> dependencyHandler
        1 * configurationContainer.migratingUnlocked('classpath', ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE) >> configuration
        1 * classpathResolver.prepareClassPath(configuration, dependencyHandler)
        1 * classpathResolver.resolveClassPath(configuration) >> classpath
    }

    def "script classpath queries runtime classpath"() {
        when:
        def result = handler.scriptClassPath

        then:
        result == ClasspathUtil.getClasspath(classLoaderScope.localClassLoader)
    }

    def "can configure repositories"() {
        def configure = {
            mavenCentral()
        }

        when:
        handler.repositories(configure)

        then:
        1 * depMgmtServices.resolveRepositoryHandler >> repositoryHandler
        1 * repositoryHandler.configure(configure) >> { ConfigureUtil.configureSelf(configure, repositoryHandler) }
        1 * repositoryHandler.mavenCentral()
    }

    def "can configure dependencies"() {
        when:
        handler.dependencies {
            add('config', 'dep')
        }

        then:
        1 * depMgmtServices.dependencyHandler >> dependencyHandler
        1 * depMgmtServices.configurationContainer >> configurationContainer
        1 * configurationContainer.migratingUnlocked('classpath', ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE) >> configuration
        1 * classpathResolver.prepareClassPath(configuration, dependencyHandler)
        1 * dependencyHandler.add('config', 'dep')
    }
}
