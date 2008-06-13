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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.tasks.Clean
import org.gradle.api.tasks.Resources
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Bundle
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.Task

/**
 * @author Hans Dockter
 */
class JavaPlugin implements Plugin {
    static final String RESOURCES = 'resources'
    static final String COMPILE = 'compile'
    static final String TEST_RESOURCES = 'testResources'
    static final String TEST_COMPILE = 'testCompile'
    static final String TEST = 'test'
    static final String LIBS = 'libs'
    static final String DISTS = 'dists'
    static final String UPLOAD_LIBS = 'uploadLibs'
    static final String CLEAN = 'clean'
    static final String INIT = 'init'
    static final String JAVADOC = 'javadoc'

    static final String RUNTIME = 'runtime'
    static final String TEST_RUNTIME = 'testRuntime'
    static final String DEFAULT = 'default'
    static final String UPLOAD_DISTS = 'uploadDists'

    void apply(Project project, PluginRegistry pluginRegistry, Map customValues = [:]) {
        def javaConvention = new JavaPluginConvention(project, customValues)
        Convention convention = project.convention
        convention.plugins.java = javaConvention

        configureDependencyManager(project)

        project.status = 'integration'

        project.createTask(INIT)

        project.createTask(CLEAN, type: Clean).conventionMapping(DefaultConventionsToPropertiesMapping.CLEAN)

        project.createTask(JAVADOC, type: Javadoc).conventionMapping(DefaultConventionsToPropertiesMapping.JAVADOC)

        project.createTask(RESOURCES, type: Resources, dependsOn: INIT).conventionMapping(DefaultConventionsToPropertiesMapping.RESOURCES)

        configureCompile(project.createTask(COMPILE, dependsOn: RESOURCES, type: Compile),
                DefaultConventionsToPropertiesMapping.COMPILE)

        project.createTask(TEST_RESOURCES, dependsOn: COMPILE, type: Resources).configure {
            skipProperties << "$Task.AUTOSKIP_PROPERTY_PREFIX$TEST"
            conventionMapping(DefaultConventionsToPropertiesMapping.TEST_RESOURCES)
        }

        configureTestCompile(project.createTask(TEST_COMPILE, dependsOn: TEST_RESOURCES, type: Compile),
                project.task(COMPILE),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE)

        project.createTask(TEST, dependsOn: TEST_COMPILE, type: Test).configure {
            conventionMapping(DefaultConventionsToPropertiesMapping.TEST)
            doFirst {Test test ->
                test.unmanagedClasspath(test.project.task(TEST_COMPILE).unmanagedClasspath as Object[])
            }
        }

        project.createTask(LIBS, type: Bundle, dependsOn: TEST).configure {
            conventionMapping(DefaultConventionsToPropertiesMapping.LIB)
            jar()
        }

        project.createTask(UPLOAD_LIBS, type: Upload, dependsOn: LIBS).configure {
            bundles << project.task(LIBS)
            uploadResolvers.add(project.dependencies.buildResolver)
            uploadModuleDescriptor = true
        }

        project.createTask(DISTS, type: Bundle, dependsOn: UPLOAD_LIBS).configure {
            conventionMapping(DefaultConventionsToPropertiesMapping.DIST)
        }

        project.createTask(UPLOAD_DISTS, type: Upload, dependsOn: DISTS).configure {
            configurations << DISTS
        }
    }

    void configureDependencyManager(Project project) {
        project.dependencies {
            addConfiguration(new Configuration(COMPILE, Visibility.PRIVATE, null, null, false, null))
            addConfiguration(new Configuration(RUNTIME, Visibility.PRIVATE, null, [COMPILE] as String[], true, null))
            addConfiguration(new Configuration(TEST_COMPILE, Visibility.PRIVATE, null, [COMPILE] as String[], false, null))
            addConfiguration(new Configuration(TEST_RUNTIME, Visibility.PRIVATE, null, [RUNTIME, TEST_COMPILE] as String[], true, null))
            addConfiguration(new Configuration(LIBS, Visibility.PUBLIC, null, null, true, null))
            addConfiguration(new Configuration(DEFAULT, Visibility.PUBLIC, null, [RUNTIME, LIBS] as String[], true, null))
            addConfiguration(new Configuration(DISTS, Visibility.PUBLIC, null, null, true, null))
            artifactProductionTaskName = UPLOAD_LIBS
            artifactPatterns << ("${project.buildDir.absolutePath}/[artifact]-[revision](-[classifier]).[ext]" as String)
            artifactPatterns << ("${project.convention.plugins.java.distsDir}/[artifact]-[revision](-[classifier]).[ext]" as String)
            addConf2Tasks(COMPILE, COMPILE)
            addConf2Tasks(RUNTIME, TEST)
            addConf2Tasks(TEST_COMPILE, TEST_COMPILE)
            addConf2Tasks(TEST_RUNTIME, TEST)
        }
    }

    protected Compile configureTestCompile(Compile testCompile, Compile compile, Map propertyMapping) {
        testCompile.skipProperties << "$Task.AUTOSKIP_PROPERTY_PREFIX$TEST"
        configureCompile(testCompile, propertyMapping)
        testCompile.doFirst {
            it.unmanagedClasspath(compile.unmanagedClasspath as Object[])
        }
    }

    protected Compile configureCompile(Compile compile, Map propertyMapping) {
        compile.configure {
            conventionMapping(propertyMapping)
        }
        compile
    }

}
