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
import org.gradle.api.Task
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.execution.Dag
import org.gradle.groovy.scripts.EmptyScript
import org.gradle.util.GradleUtil



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
                new File(""),
                parent,
                null,
                null,
                new TaskFactory(),
                new DefaultDependencyManagerFactory(new File('root')),
                null,
                null,
                parent.projectRegistry] as Object[])
    }

    static DefaultProject createRootProject(File rootDir) {
        ProjectRegistry projectRegistry = new ProjectRegistry()
        TaskFactory taskFactory = new TaskFactory();
        DefaultProject project = new DefaultProject(
                rootDir.name,
                null,
                rootDir,
                null,
                null,
                null,
                taskFactory,
                new DefaultDependencyManagerFactory(new File('root')),
                new BuildScriptProcessor(),
                new PluginRegistry(),
                projectRegistry)
        project.setBuildScript(new EmptyScript());
        return project;
    }

    static DefaultProject createChildProject(DefaultProject parentProject, String name) {
        return new DefaultProject(
                name,
                parentProject,
                parentProject.rootDir,
                parentProject.rootProject,
                parentProject.buildFileName,
                parentProject.buildScriptClassLoader,
                parentProject.taskFactory,
                parentProject.dependencyManagerFactory,
                parentProject.buildScriptProcessor,
                parentProject.pluginRegistry,
                parentProject.projectRegistry)
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

    static DefaultExcludeRule getTestExcludeRules() {
        new DefaultExcludeRule(new ArtifactId(
                new ModuleId('org', 'module'), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null)
    }

    static DefaultDependencyDescriptor getTestDescriptor() {
        new DefaultDependencyDescriptor(ModuleRevisionId.newInstance('org', 'name', 'rev'), false)
    }

    static Script createTestScript() {
        new MyScript()        
    }

    static Closure createTaskActionClosure() {
        return { Task task, Dag dag -> }
}
}

class MyScript extends Script {
    Object run() {
        return null;  
    }
}