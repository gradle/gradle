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
import org.gradle.StartParameter
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.artifacts.dependencies.DefaultModuleDependency
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultClientModuleFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyFactory
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.plugins.DefaultConvention
import org.gradle.api.plugins.Convention
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.Spec
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.ScriptWithSource
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.invocation.DefaultBuild
import org.gradle.logging.AntLoggingAdapter
import org.gradle.util.GradleUtil
import org.gradle.util.WrapUtil
import org.gradle.api.internal.project.*
import org.gradle.configuration.DefaultProjectEvaluator
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultProjectDependencyFactory

/**
 * @author Hans Dockter
 * todo: deleteTestDir throws an exception if dir does not exists. failonerror attribute seems not to work. Check this out.
 */
class HelperUtil {

    public static final Closure TEST_CLOSURE = {}
    public static final String TMP_DIR_FOR_TEST = 'tmpTest'
    public static final Spec TEST_SEPC  = new AndSpec()

    static DefaultProject createProjectMock(Map closureMap, String projectName, DefaultProject parent) {
      Convention convention = new DefaultConvention()
      return ProxyGenerator.instantiateAggregate(closureMap, null, DefaultProject, [
                projectName,
                parent,
                new File("projectDir"),
                "build.gradle",
                new StringScriptSource("test build file", null),
                null,
                new TaskFactory(),
                new DefaultConfigurationContainerFactory(new File('root')),
                new DefaultDependencyFactory([] as Set, new DefaultClientModuleFactory()),
                new DefaultRepositoryHandlerFactory(new DefaultResolverFactory(), convention),
                new DefaultPublishArtifactFactory(),
                new DefaultAntBuilderFactory(new AntLoggingAdapter()),
                null,
                null,
                parent.projectRegistry,
                null,
                null,
                convention] as Object[])
    }

    static DefaultProject createRootProject() {
        createRootProject(makeNewTestDir())
    }

    static DefaultProject createRootProject(File rootDir) {
      IProjectFactory projectFactory = new ProjectFactory(
                new TaskFactory(),
                new DefaultConfigurationContainerFactory(),
                new DefaultDependencyFactory([] as Set, new DefaultClientModuleFactory(), new DefaultProjectDependencyFactory()),
                new DefaultRepositoryHandlerFactory(new DefaultResolverFactory()),
                new DefaultPublishArtifactFactory(),
                [:] as InternalRepository,
                new DefaultProjectEvaluator(null, null, null),
                new PluginRegistry(),
                new StringScriptSource("embedded build file", "embedded"),
                new DefaultAntBuilderFactory(new AntLoggingAdapter()))

        DefaultBuild build = new DefaultBuild(new StartParameter(), null, null)
        DefaultProjectDescriptor descriptor = new DefaultProjectDescriptor(null, rootDir.name, rootDir,
                new DefaultProjectDescriptorRegistry())
        DefaultProject project = projectFactory.createProject(descriptor, null, build)
        project.setBuildScript(new EmptyScript())
        return project;
    }

    static DefaultProject createChildProject(DefaultProject parentProject, String name) {
        Convention convention = new DefaultConvention()
        DefaultProject project = new DefaultProject(
                name,
                parentProject,
                new File("projectDir" + name),
                parentProject.buildFile,
                new StringScriptSource("test build file", null),
                parentProject.buildScriptClassLoader,
                new TaskFactory(),
                parentProject.configurationContainerFactory,
                new DefaultDependencyFactory([] as Set, new DefaultClientModuleFactory(), new DefaultProjectDependencyFactory()),
                new DefaultRepositoryHandlerFactory(new DefaultResolverFactory()),
                new DefaultPublishArtifactFactory(),
                null,
                parentProject.getAntBuilderFactory(),
                parentProject.projectEvaluator,
                parentProject.pluginRegistry,
                parentProject.projectRegistry,
                parentProject.build,
                convention)
        parentProject.addChildProject project
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

    static File makeNewTestDir() {
        GradleUtil.makeNewDir(new File(TMP_DIR_FOR_TEST))
    }

    static File makeNewTestDir(String dirName) {
        GradleUtil.makeNewDir(new File(TMP_DIR_FOR_TEST, dirName))
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

    static Dependency createDependency(String group, String name, String version) {
      new DefaultModuleDependency(group, name, version)
    }

    static DefaultPublishArtifact createPublishArtifact(String name, String extension, String type, String classifier) {
      new DefaultPublishArtifact(name, extension, type, classifier, new Date(), new File(""))
    }

    static Script createTestScript() {
        new MyScript()
    }

    static Script createScript(String code) {
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
        configuration.setScriptBaseClass(ScriptWithSource.getName());

        GroovyShell shell = new GroovyShell(configuration)
        ScriptWithSource script = shell.parse(source.getText())
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

    // todo should probably be removed
    static Set<DefaultConfiguration> createConfigurations(String confName1, String confName2) {
        return WrapUtil.toSet(
                new DefaultConfiguration(confName1, null),
                new DefaultConfiguration(confName2, null));
    }

    static String createUniqueId() {
        return new UID().toString();
    }

    static org.gradle.api.artifacts.Configuration createConfiguration(String name) {
        return new DefaultConfiguration(name, null, null, null, null, null)
    }
}

public interface TestClosure {
    Object call(Object param);
}

class MyScript extends Script {
    Object run() {
        return null;  
    }
}