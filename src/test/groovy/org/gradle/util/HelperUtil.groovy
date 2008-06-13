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

import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.util.GradleUtil
import org.apache.tools.ant.taskdefs.condition.Os
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.gradle.api.internal.dependencies.DependenciesUtil
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.matcher.ExactPatternMatcher

/**
 * @author Hans Dockter
 * todo: deleteTestDir throws an exception if dir does not exists. failonerror attribute seems not to work. Check this out.
 */
class HelperUtil {
    static final String TMP_DIR_FOR_TEST = 'tmpTest'

    static DefaultProject createProjectMock(Map closureMap, String projectName, DefaultProject parent) {
        return ProxyGenerator.instantiateAggregate(closureMap, null, DefaultProject, [projectName, parent, new File(""),
                parent, new ProjectFactory(new DefaultDependencyManagerFactory(new File('root'))), new DefaultDependencyManager(), null, null, null] as Object[])
    }

    static DefaultProject createRootProject(File rootDir) {
        return new DefaultProject(rootDir.name, null, rootDir, null, new ProjectFactory(new DefaultDependencyManagerFactory(new File('root'))), new DefaultDependencyManagerFactory(new File('root')).createDependencyManager(), new BuildScriptProcessor(), new BuildScriptFinder(), new PluginRegistry())
    }

    static DefaultProject createChildProject(DefaultProject parentProject, String name) {
        return new DefaultProject(name, parentProject, parentProject.rootDir, parentProject.rootProject,
                parentProject.projectFactory, parentProject.dependencies, parentProject.buildScriptProcessor,
                parentProject.buildScriptFinder, parentProject.pluginRegistry)
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
        new DefaultDependencyDescriptor(DependenciesUtil.moduleRevisionId('org', 'name', 'rev'), false)
    }
}