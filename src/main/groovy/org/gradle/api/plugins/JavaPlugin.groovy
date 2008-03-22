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

package org.gradle.api.plugins

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.Configuration.Visibility
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.publish.PublishOptions
import org.gradle.api.DependencyManager
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.tasks.Clean
import org.gradle.api.tasks.Resources
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.api.tasks.compile.AntJavac
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class JavaPlugin implements Plugin {
    static final String RESOURCES = 'resources'
    static final String COMPILE = 'compile'
    static final String TEST_RESOURCES = 'testResources'
    static final String TEST_COMPILE = 'testCompile'
    static final String TEST = 'test'
    static final String LIB = 'lib'
    static final String DIST = 'dist'
    static final String INSTALL = 'install'
    static final String CLEAN = 'clean'

    static final String RUNTIME = 'runtime'
    static final String TEST_RUNTIME = 'testRuntime'
    static final String MASTER = 'master'
    static final String DEFAULT = 'default'
    static final String DISTRIBUTE = 'distribute'

    void apply(Project project, PluginRegistry pluginRegistry, def convention = null) {
        def javaConvention = convention ?: new JavaConvention(project)

        configureDependencyManager(javaConvention, project)

        project.convention = javaConvention

        project.status = 'integration'

        project.createTask(CLEAN, type: Clean).convention(javaConvention, DefaultConventionsToPropertiesMapping.CLEAN)

        project.createTask(RESOURCES, type: Resources).convention(javaConvention, DefaultConventionsToPropertiesMapping.RESOURCES)

        configureCompile(project.createTask(COMPILE, dependsOn: RESOURCES, type: Compile), javaConvention,
                DefaultConventionsToPropertiesMapping.COMPILE)

        project.createTask(TEST_RESOURCES, dependsOn: COMPILE, type: Resources).convention(javaConvention, DefaultConventionsToPropertiesMapping.TEST_RESOURCES)

        configureCompile(project.createTask(TEST_COMPILE, dependsOn: TEST_RESOURCES, type: Compile), javaConvention,
                DefaultConventionsToPropertiesMapping.TEST_COMPILE)

        project.createTask(TEST, dependsOn: TEST_COMPILE, type: Test).configure {
            delegate.convention(javaConvention, DefaultConventionsToPropertiesMapping.TEST)
        }

        Closure lateInitClosureForPackage = {Bundle bundle ->
            if (project.hasProperty('type') && project.type) {
                bundle.createArchive(javaConvention.archiveTypes[project.type]) {
                    resourceCollections << [new FileSet(javaConvention.classesDir)]
                }
            }
        }
        project.createTask(LIB, type: Bundle, lateInitializer: [lateInitClosureForPackage], dependsOn: TEST).configure {
            delegate.convention(javaConvention, DefaultConventionsToPropertiesMapping.LIB)
        }

        project.createTask(DIST, type: Bundle, dependsOn: LIB).configure {
            delegate.convention(javaConvention, DefaultConventionsToPropertiesMapping.DIST)
        }

        project.createTask(INSTALL, dependsOn: LIB) {Task task ->
            DependencyManager deps = task.project.dependencies
            File ivyFile = project.file("${task.project.convention.buildDir}/ivy.xml")
            DefaultModuleDescriptor moduleDescriptor = deps.moduleDescriptorConverter.convert(deps)
            moduleDescriptor.toIvyFile(ivyFile)
            PublishOptions publishOptions = new PublishOptions()
            publishOptions.setOverwrite(true)
            publishOptions.srcIvyPattern = ivyFile.absolutePath
            deps.ivy.publishEngine.publish(deps.moduleDescriptorConverter.convert(deps),
                    deps.artifactPatterns.collect {project.file(it).absolutePath}, deps.ivy.settings.getResolver(DependencyManager.BUILD_RESOLVER_NAME), publishOptions)
        }

        project.createTask(DISTRIBUTE, dependsOn: DIST) {Task task ->
            DependencyManager deps = task.project.dependencies
            File ivyFile = project.file("${task.project.convention.buildDir}/ivy.xml")
            DefaultModuleDescriptor moduleDescriptor = deps.moduleDescriptorConverter.convert(deps)
            moduleDescriptor.toIvyFile(ivyFile)
            PublishOptions publishOptions = new PublishOptions()
            publishOptions.setOverwrite(true)
            publishOptions.confs = [DISTRIBUTE]
            deps.uploadResolvers.resolverList.each { resolver ->
                deps.ivy.publishEngine.publish(deps.moduleDescriptorConverter.convert(deps),
                        deps.artifactPatterns.collect {pattern -> project.file(pattern).absolutePath}, resolver, publishOptions)
            }
        }
    }

    void configureDependencyManager(JavaConvention convention, Project project) {
        project.dependencies {
            addConfiguration(new Configuration(COMPILE, Visibility.PRIVATE, null, null, false, null))
            addConfiguration(new Configuration(RUNTIME, Visibility.PRIVATE, null, [COMPILE] as String[], true, null))
            addConfiguration(new Configuration(TEST_COMPILE, Visibility.PRIVATE, null, [COMPILE] as String[], true, null))
            addConfiguration(new Configuration(TEST_RUNTIME, Visibility.PRIVATE, null, [RUNTIME, TEST_COMPILE] as String[], true, null))
            addConfiguration(new Configuration(MASTER, Visibility.PUBLIC, null, null, true, null))
            addConfiguration(new Configuration(DEFAULT, Visibility.PUBLIC, null, [RUNTIME, MASTER] as String[], true, null))
            addConfiguration(new Configuration(DISTRIBUTE, Visibility.PUBLIC, null, null, true, null))
            artifactProductionTaskName = INSTALL
            artifactPatterns << ("${convention.buildDir.absolutePath}/[artifact]-[revision].[ext]" as String)
            artifactPatterns << ("${convention.buildDir.absolutePath}/distribution/[artifact]-[revision].[ext]" as String)
            addConf2Tasks(RUNTIME, TEST)
            resolvers.add([name: 'Maven2Repo', url: 'http://repo1.maven.org/maven2/'])
        }
    }

    protected Compile configureTestCompile(Compile compile, def javaConvention, Map propertyMapping) {
        compile.skipProperties << Test.SKIP_TEST
        configureCompile(compile, javaConvention, propertyMapping)
    }

    protected Compile configureCompile(Compile compile, def javaConvention, Map propertyMapping) {
        compile.configure {
            convention(javaConvention, propertyMapping)
            antCompile = new AntJavac()
        }
        compile
    }

}
