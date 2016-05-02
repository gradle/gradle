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
import org.gradle.api.internal.file.FileLookup
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.initialization.loadercache.ClassPathSnapshot
import org.gradle.api.internal.initialization.loadercache.ClassPathSnapshotter
import org.gradle.api.internal.plugins.dsl.PluginRepositoryHandler
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectScript
import org.gradle.groovy.scripts.*
import org.gradle.groovy.scripts.internal.BuildScriptData
import org.gradle.groovy.scripts.internal.FactoryBackedCompileOperation
import org.gradle.internal.Factory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequests
import spock.lang.Specification

public class DefaultScriptPluginFactoryTest extends Specification {

    def scriptCompilerFactory = Mock(ScriptCompilerFactory)
    def scriptCompiler = Mock(ScriptCompiler)
    def scriptSource = Mock(ScriptSource)
    def scriptRunner = Mock(ScriptRunner)
    def script = Mock(BasicScript)
    def instantiator = Mock(Instantiator)
    def targetScope = Mock(ClassLoaderScope)
    def baseScope = Mock(ClassLoaderScope)
    def scopeClassLoader = Mock(ClassLoader)
    def baseChildClassLoader = Mock(ClassLoader)
    def scriptHandlerFactory = Mock(ScriptHandlerFactory)
    def pluginRequestApplicator = Mock(PluginRequestApplicator)
    def scriptHandler = Mock(ScriptHandlerInternal)
    def classPathScriptRunner = Mock(ScriptRunner)
    def loggingManagerFactory = Mock(Factory) as Factory<LoggingManagerInternal>
    def loggingManager = Mock(LoggingManagerInternal)
    def fileLookup = Mock(FileLookup)
    def directoryFileTreeFactory = Mock(DirectoryFileTreeFactory)
    def documentationRegistry = Mock(DocumentationRegistry)
    def classPathSnapshotter = Mock(ClassPathSnapshotter)
    def pluginRepositoryHandler = Mock(PluginRepositoryHandler)

    def factory = new DefaultScriptPluginFactory(scriptCompilerFactory, loggingManagerFactory, instantiator, scriptHandlerFactory, pluginRequestApplicator, fileLookup,
        directoryFileTreeFactory, documentationRegistry, new ModelRuleSourceDetector(), pluginRepositoryHandler)

    def setup() {
        def configurations = Mock(ConfigurationContainer)
        scriptHandler.configurations >> configurations
        scriptHandler.scriptClassPath >> Mock(ClassPath)
        classPathScriptRunner.data >> Mock(PluginRequests) {
            isEmpty() >> true
        }
        def configuration = Mock(Configuration)
        configurations.getByName(ScriptHandler.CLASSPATH_CONFIGURATION) >> configuration
        configuration.getFiles() >> Collections.emptySet()
        baseScope.getExportClassLoader() >> baseChildClassLoader
        def snapshot = Mock(ClassPathSnapshot)
        classPathSnapshotter.snapshot(_) >> snapshot
        snapshot.hashCode() >> 123

        1 * targetScope.getLocalClassLoader() >> scopeClassLoader
    }

    void configuresATargetObjectUsingScript() {
        when:
        final Object target = new Object()

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(DefaultScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(DefaultScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, false)
        configurer.apply(target)
    }

    void configuresAProjectObjectUsingScriptWithImperativeAndInheritableCode() {
        when:
        def target = Mock(ProjectInternal)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> true
        1 * scriptRunner.script >> script
        1 * target.setScript(script)
        0 * target.addDeferredConfiguration(_)
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)
    }

    void configuresAProjectObjectUsingScriptWithImperativeCode() {
        when:
        def target = Mock(ProjectInternal)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(true)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> false
        0 * target.setScript(_)
        0 * target.addDeferredConfiguration(_)
        1 * scriptRunner.run(target, _ as ServiceRegistry)
        0 * scriptRunner._

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)
    }

    void configuresAProjectObjectUsingScriptWithInheritableAndDeferredCode() {
        when:
        def target = Mock(ProjectInternal)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> true
        1 * scriptRunner.script >> script
        1 * target.setScript(script)
        1 * target.addDeferredConfiguration(_)
        0 * scriptRunner._

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)
    }

    void configuresAProjectObjectUsingScriptWithDeferredCode() {
        when:
        def target = Mock(ProjectInternal)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> true
        _ * scriptRunner.hasMethods >> false
        0 * target.setScript(_)
        1 * target.addDeferredConfiguration(_)
        0 * scriptRunner._

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)
    }

    void configuresAProjectObjectUsingEmptyScript() {
        when:
        def target = Mock(ProjectInternal)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * scriptCompilerFactory.createCompiler(scriptSource) >> scriptCompiler
        1 * scriptCompiler.compile(ProjectScript, _ as FactoryBackedCompileOperation, baseChildClassLoader, _) >> classPathScriptRunner
        1 * classPathScriptRunner.run(target, _ as ServiceRegistry)
        1 * scriptCompiler.compile(ProjectScript, { it.transformer != null }, scopeClassLoader, !null) >> scriptRunner
        _ * scriptRunner.data >> new BuildScriptData(false)
        _ * scriptRunner.runDoesSomething >> false
        _ * scriptRunner.hasMethods >> false
        0 * scriptRunner._
        0 * target.setScript(_)
        0 * target.addDeferredConfiguration(_)

        then:
        def configurer = factory.create(scriptSource, scriptHandler, targetScope, baseScope, true)
        configurer.apply(target)
    }
}
