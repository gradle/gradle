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

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.util.GradleUtil
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.StartParameter
import org.gradle.CacheUsage
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.Configuration
import org.gradle.invocation.DefaultBuild
import org.gradle.logging.AntLoggingAdapter
import org.gradle.api.internal.dependencies.DefaultDependencyConfigurationMappingContainer
import org.gradle.initialization.ISettingsFinder



/**
 * @author Hans Dockter
 * todo: deleteTestDir throws an exception if dir does not exists. failonerror attribute seems not to work. Check this out.
 */
class HelperUtil {
    public static final String TMP_DIR_FOR_TEST = 'tmpTest'

    static DefaultProject createProjectMock(Map closureMap, String projectName, DefaultProject parent) {
        return ProxyGenerator.instantiateAggregate(closureMap, null, DefaultProject, [
                projectName,
                parent,
                new File("projectDir"),
                "build.gradle",
                new StringScriptSource("test build file", null),
                null,
                new TaskFactory(),
                new DefaultDependencyManagerFactory(new File('root')),
                new DefaultAntBuilderFactory(new AntLoggingAdapter()),
                null,
                null,
                parent.projectRegistry,
                null,
                null] as Object[])
    }

    static DefaultProject createRootProject(File rootDir) {
        ISettingsFinder settingsFinder = [getSettingsDir: { new File('root') }] as ISettingsFinder
        IProjectFactory projectFactory = new ProjectFactory(
                new TaskFactory(),
                new DefaultDependencyManagerFactory(settingsFinder, CacheUsage.ON),
                new BuildScriptProcessor(),
                new PluginRegistry(),
                new StartParameter(),
                new StringScriptSource("embedded build file", "embedded"),
                new DefaultAntBuilderFactory(new AntLoggingAdapter()))

        DefaultBuild build = new DefaultBuild(new StartParameter(), null)
        DefaultProject project = projectFactory.createProject(rootDir.name, null, rootDir, build)
        project.setBuildScript(new EmptyScript())
        return project;
    }

    static DefaultProject createChildProject(DefaultProject parentProject, String name) {
        return new DefaultProject(
                name,
                parentProject,
                new File("projectDir" + name),
                parentProject.buildFileName,
                new StringScriptSource("test build file", null),
                parentProject.buildScriptClassLoader,
                parentProject.taskFactory,
                parentProject.dependencyManagerFactory,
                parentProject.getAntBuilderFactory(),
                parentProject.buildScriptProcessor,
                parentProject.pluginRegistry,
                parentProject.projectRegistry,
                parentProject.projectFactory,
                parentProject.build)
    }

    static org.gradle.StartParameter dummyStartParameter() {
        return new StartParameter(
                "settingsFileName",
                "buildFileName",
                ["onetask", "secondTask"],
                new File("currentDir"),
                true,
                [:],
                [:],
                new File("gradleUserHome"),
                new File("defaultImports"),
                new File("pluginProperties"),
                CacheUsage.ON
        );
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

    static DefaultModuleDescriptor getTestModuleDescriptor(Set confs) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(ModuleRevisionId.newInstance('org', 'name', 'rev'), "status", null)
        confs.each { moduleDescriptor.addConfiguration(new Configuration(it)) }
        return moduleDescriptor;
    }

    static Script createTestScript() {
        new MyScript()        
    }

    static Closure toClosure(String text) {
        return new GroovyShell().evaluate("return " + text)
    }

    static Closure toClosure(TestClosure closure) {
        return { param -> closure.call(param) }
    }

    static Closure returns(Object value) {
        return { value }
    }

    static DefaultDependencyConfigurationMappingContainer getConfMappings(def confsCollection) {
        DefaultDependencyConfigurationMappingContainer testConfigurationMappings = new DefaultDependencyConfigurationMappingContainer()
        testConfigurationMappings.addMasters(confsCollection as String[])
        testConfigurationMappings
    }

    static Closure createSetterClosure(String name, String value) {
        return {
            "set$name"(value)
        }
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