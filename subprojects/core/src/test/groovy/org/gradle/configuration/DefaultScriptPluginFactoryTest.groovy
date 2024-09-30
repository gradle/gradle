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
package org.gradle.configuration

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectScript
import org.gradle.configuration.project.DefaultCompileOperationFactory
import org.gradle.groovy.scripts.BasicScript
import org.gradle.groovy.scripts.DefaultScript
import org.gradle.groovy.scripts.ScriptCompiler
import org.gradle.groovy.scripts.ScriptCompilerFactory
import org.gradle.groovy.scripts.ScriptRunner
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.BuildScriptData
import org.gradle.groovy.scripts.internal.NoDataCompileOperation
import org.gradle.internal.Factory
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.plugin.management.internal.PluginHandler
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import spock.lang.Specification

class DefaultScriptPluginFactoryTest extends Specification {

    def scriptCompilerFactory = Mock(ScriptCompilerFactory)
    def scriptCompiler = Mock(ScriptCompiler)
    def scriptSource = Mock(ScriptSource)
    def scriptRunner = Mock(ScriptRunner)
    def script = Mock(BasicScript)
    def targetScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)
    def baseChildClassLoader = Mock(ClassLoader)
    def pluginRequestApplicator = Mock(PluginRequestApplicator)
    def scriptHandler = Mock(ScriptHandlerInternal)
    def classPathScriptRunner = Mock(ScriptRunner)
    def loggingManagerFactory = Mock(Factory) as Factory<LoggingManagerInternal>
    def loggingManager = Mock(LoggingManagerInternal)
    def documentationRegistry = Mock(DocumentationRegistry)
    def classpathHasher = Mock(ClasspathHasher)
    def pluginHandler = Mock(PluginHandler)
    def compileOperationsFactory = new DefaultCompileOperationFactory(documentationRegistry)

    def factory = new DefaultScriptPluginFactory(
        new DefaultServiceRegistry(),
        scriptCompilerFactory,
        loggingManagerFactory,
        pluginHandler,
        pluginRequestApplicator,
        compileOperationsFactory
    )

    def setup() {
        def configurations = Mock(ConfigurationContainer)
        scriptHandler.configurations >> configurations
        scriptHandler.scriptClassPath >> Mock(ClassPath)
        classPathScriptRunner.data >> PluginRequests.EMPTY
        def configuration = Mock(Configuration)
        configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION) >> configuration
        configuration.getFiles() >> Collections.emptySet()
        baseScope.getExportClassLoader() >> baseChildClassLoader
        classpathHasher.hash(_ as ClassPath) >> TestHashCodes.hashCodeFrom(123)
    }

    void "configures a target object using script"() {
        given:
        final Object target = new Object()

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, false)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(DefaultScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(DefaultScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._
    }

    void "configures a project object using script with imperative and inheritable code"() {
        given:
        def target = Mock(ProjectInternal) {
            getExtensions() >> Mock(ExtensionContainerInternal)
        }

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> true
        1 * scriptRunner.script >> script
        1 * target.setScript(script)
        0 * target.addDeferredConfiguration(_)
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._
    }

    void "configures a project object using script with imperative code"() {
        given:
        def target = Mock(ProjectInternal) {
            getExtensions() >> Mock(ExtensionContainerInternal)
        }

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> false
        0 * target.setScript(_)
        0 * target.addDeferredConfiguration(_)
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._
    }

    void "configures a project object using script with inheritable and deferred code"() {
        given:
        def target = Mock(ProjectInternal) {
            getExtensions() >> Mock(ExtensionContainerInternal)
        }

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> true
        1 * scriptRunner.script >> script
        1 * target.setScript(script)
        1 * target.addDeferredConfiguration(_)
        0 * scriptRunner._
    }

    void "configures a project object using script with deferred code"() {
        given:
        def target = Mock(ProjectInternal) {
            getExtensions() >> Mock(ExtensionContainerInternal)
        }

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> false
        0 * target.setScript(_)
        1 * target.addDeferredConfiguration(_)
        0 * scriptRunner._
    }

    void "configures a project object using empty script"() {
        given:
        def target = Mock(ProjectInternal) {
            getExtensions() >> Mock(ExtensionContainerInternal)
        }

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> false
        _ * scriptRunner.hasMethods >> false
        0 * scriptRunner._
    }

    void "configured target uses given script plugin factory for nested scripts"() {
        given:
        def otherScriptPluginFactory = Mock(ScriptPluginFactory)
        factory.setScriptPluginFactory(otherScriptPluginFactory)
        final Object target = new Object()

        when:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, false)
        configurer.apply(target)

        then:
        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(DefaultScript, target, baseScope, _ as NoDataCompileOperation, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(DefaultScript, target, targetScope, { it.transformer != null }, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        1 * scriptRunner.run(target, { scriptServices -> scriptServices.get(ScriptPluginFactory) == otherScriptPluginFactory })
        0 * scriptRunner._
    }
}
