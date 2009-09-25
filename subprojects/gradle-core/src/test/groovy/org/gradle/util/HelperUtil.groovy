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

package org.gradle.util

import java.rmi.server.UID
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.BuildResult
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.GroovySourceGenerationBackedClassGenerator
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultClientModuleFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.SelfResolvingDependencyFactory
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.Spec
import org.gradle.configuration.DefaultProjectEvaluator
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.Script
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.invocation.DefaultGradle
import org.gradle.api.internal.project.*
import org.gradle.listener.DefaultListenerManager
import org.gradle.integtests.TestFile
import org.gradle.api.internal.artifacts.ivyservice.DefaultSettingsConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultModuleDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultConfigurationsToModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependenciesToModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultClientModuleDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultArtifactsToModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyFactory
import org.gradle.api.internal.artifacts.ivyservice.SelfResolvingDependencyResolver
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyResolver
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyReportConverter
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyDependencyPublisher
import org.gradle.api.internal.artifacts.ivyservice.DefaultModuleDescriptorForUploadConverter
import org.gradle.api.internal.artifacts.ivyservice.DefaultPublishOptionsFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultExcludeRuleConverter

/**
 * @author Hans Dockter
 * todo: deleteTestDir throws an exception if dir does not exists. failonerror attribute seems not to work. Check this out.
 */
class HelperUtil {

    public static final Closure TEST_CLOSURE = {}
    public static final String TMP_DIR_FOR_TEST = 'tmpTest'
    public static final Spec TEST_SEPC  = new AndSpec()
    private static final ITaskFactory taskFactory = new AnnotationProcessingTaskFactory(new TaskFactory(new GroovySourceGenerationBackedClassGenerator()));

    static <T extends Task> T createTask(Class<T> type) {
        return createTask(type, createRootProject())
    }
    
    static <T extends Task> T createTask(Class<T> type, Project project) {
        return taskFactory.createTask(project, [name: 'name', type: type])
    }

    static DefaultProject createRootProject() {
        createRootProject(makeNewTestDir())
    }

    static DefaultProject createRootProject(File rootDir) {
        StartParameter startParameter = new StartParameter()
        startParameter.pluginPropertiesFile = new File('plugin.properties')
        DefaultRepositoryHandlerFactory repositoryHandlerFactory = new DefaultRepositoryHandlerFactory(new DefaultResolverFactory(), new GroovySourceGenerationBackedClassGenerator())
        DefaultDependencyFactory dependencyFactory = new DefaultDependencyFactory(
                [new SelfResolvingDependencyFactory()] as Set,
                new DefaultClientModuleFactory(),
                new DefaultProjectDependencyFactory(startParameter.projectDependenciesBuildInstruction))
        Map clientModuleRegistry = new HashMap();
        ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();
        DefaultServiceRegistryFactory serviceRegistryFactory = new DefaultServiceRegistryFactory(
                repositoryHandlerFactory,
                new DefaultConfigurationContainerFactory(clientModuleRegistry,
                new DefaultSettingsConverter(),
                new DefaultModuleDescriptorConverter(
                        new DefaultModuleDescriptorFactory(),
                        new DefaultConfigurationsToModuleDescriptorConverter(),
                        new DefaultDependenciesToModuleDescriptorConverter(
                                new DefaultDependencyDescriptorFactory(excludeRuleConverter,
                                        new DefaultClientModuleDescriptorFactory(), clientModuleRegistry),
                                excludeRuleConverter),
                        new DefaultArtifactsToModuleDescriptorConverter()),
                new DefaultIvyFactory(),
                new SelfResolvingDependencyResolver(
                        new DefaultIvyDependencyResolver(new DefaultIvyReportConverter())),
                new DefaultIvyDependencyPublisher(new DefaultModuleDescriptorForUploadConverter(),
                        new DefaultPublishOptionsFactory())),
                new DefaultPublishArtifactFactory(),
                dependencyFactory,
                new DefaultProjectEvaluator(),
                new GroovySourceGenerationBackedClassGenerator()
        )
        IProjectFactory projectFactory = new ProjectFactory(
                serviceRegistryFactory,
                new StringScriptSource("embedded build file", "embedded"))

        DefaultListenerManager listenerManager = new DefaultListenerManager()
        DefaultInternalRepository internalRepo = new DefaultInternalRepository(listenerManager)
        internalRepo.setName('testInternalRepo') 
        DefaultGradle build = new DefaultGradle(startParameter, internalRepo, serviceRegistryFactory,
                                                new DefaultStandardOutputRedirector(), listenerManager)
        DefaultProjectDescriptor descriptor = new DefaultProjectDescriptor(null, rootDir.name, rootDir,
                new DefaultProjectDescriptorRegistry())
        DefaultProject project = projectFactory.createProject(descriptor, null, build)
        project.setScript(new EmptyScript())
        project."_service_registry_factory_" = serviceRegistryFactory
        return project;
    }

    static DefaultProject createChildProject(DefaultProject parentProject, String name, File projectDir = null) {
        DefaultProject project = new DefaultProject(
                name,
                parentProject,
                projectDir ?: new File(parentProject.getProjectDir(), name),
                parentProject.buildFile,
                new StringScriptSource("test build file", null),
                parentProject.projectRegistry,
                parentProject.gradle,
                parentProject."_service_registry_factory_"
        )
        parentProject.addChildProject project
        parentProject.projectRegistry.addProject project
        return project
    }

    static org.gradle.StartParameter dummyStartParameter() {
        StartParameter parameter = new StartParameter()
        parameter.gradleHomeDir = new File('gradle home')
        return parameter
    }

    static def pureStringTransform(def collection) {
        collection.collect {
            it.toString()
        }
    }

    static void deleteTestDir() {
        GradleUtil.deleteDir(new File(TMP_DIR_FOR_TEST))
    }

    static TestFile makeNewTestDir() {
        new TestFile(GradleUtil.makeNewDir(new File(TMP_DIR_FOR_TEST)))
    }

    static TestFile makeNewTestDir(String dirName) {
        new TestFile(GradleUtil.makeNewDir(new File(TMP_DIR_FOR_TEST, dirName)))
    }

    static File getTestDir() {
        new File(TMP_DIR_FOR_TEST)
    }

    static DefaultExcludeRule getTestExcludeRule() {
        new DefaultExcludeRule(new ArtifactId(
                new ModuleId('org', 'module'), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null)
    }

    static DefaultDependencyDescriptor getTestDescriptor() {
        new DefaultDependencyDescriptor(ModuleRevisionId.newInstance('org', 'name', 'rev'), false)
    }

    static DefaultModuleDescriptor createModuleDescriptor(Set confs) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(ModuleRevisionId.newInstance('org', 'name', 'rev'), "status", null)
        confs.each { moduleDescriptor.addConfiguration(new Configuration(it)) }
        return moduleDescriptor;
    }

    static BuildResult createBuildResult(Throwable t) {
        return new BuildResult(null, t);
    }

    static ModuleDependency createDependency(String group, String name, String version) {
      new DefaultExternalModuleDependency(group, name, version)
    }

    static DefaultPublishArtifact createPublishArtifact(String name, String extension, String type, String classifier) {
      new DefaultPublishArtifact(name, extension, type, classifier, new Date(), new File(""))
    }

    static groovy.lang.Script createScript(String code) {
        new GroovyShell().parse(code)
    }

    static Object call(String text, Object params) {
        toClosure(text).call(params)
    }
    
    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(ScriptSource source) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setScriptBaseClass(TestScript.getName());

        GroovyShell shell = new GroovyShell(configuration)
        Script script = shell.parse(source.getText())
        script.setScriptSource(source)
        return script.run()
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }

    static Closure createSetterClosure(String name, String value) {
        return {
            "set$name"(value)
        }
    }

    static String createUniqueId() {
        return new UID().toString();
    }

    static org.gradle.api.artifacts.Configuration createConfiguration(String name) {
        return new DefaultConfiguration(name, null, null)
    }
}

public interface TestClosure {
    Object call(Object param);
}

public abstract class TestScript extends Script {

    ClassLoader getContextClassloader() {
        getClass().classLoader
    }

    StandardOutputRedirector getStandardOutputRedirector() {
        null
    }
}
