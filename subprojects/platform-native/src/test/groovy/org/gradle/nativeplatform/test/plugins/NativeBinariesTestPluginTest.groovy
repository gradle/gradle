/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.test.plugins

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.DomainObjectCollectionBackedModelMap
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteSpec
import org.gradle.nativeplatform.test.internal.DefaultNativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.core.IsNot.not

class NativeBinariesTestPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(NativeComponentModelPlugin)
    }

    def "run tasks are added to the binary task list"() {
        given:
        def namingScheme = Mock(BinaryNamingScheme)
        namingScheme.getTaskName("run") >> "runTestBinary"

        def task = project.tasks.create("installTestBinary", InstallExecutable.class)
        task.destinationDir = project.projectDir
        task.executable = project.file("executable")

        def binary = BaseBinarySpec.create(TestSpec, "testBinary", project.services.get(Instantiator), Mock(ITaskFactory))
        binary.setNamingScheme(namingScheme)
        binary.tasks.add(task)

        project.binaries.add(binary)

        when:
        new NativeBinariesTestPlugin.Rules().createTestTasks(project.tasks, project.binaries)

        then:
        binary.tasks.withType(RunTestExecutable).size() == 1
        binary.tasks.run != null
    }

    def "check task depends only on buildable test binaries"() {
        given:
        def buildableNamingScheme = Mock(BinaryNamingScheme)
        buildableNamingScheme.getTaskName("run") >> "runBuildableTestBinary"

        def unbuildableNamingScheme = Mock(BinaryNamingScheme)
        unbuildableNamingScheme.getTaskName("run") >> "runUnbuildableTestBinary"

        def taskFactory = Mock(ITaskFactory)

        def installBuildableTestBinaryTask = project.tasks.create("installBuildableTestBinary", InstallExecutable.class)
        installBuildableTestBinaryTask.destinationDir = project.projectDir
        installBuildableTestBinaryTask.executable = project.file("executable1")

        def installUnbuildableTestBinaryTask = project.tasks.create("installUnbuildableTestBinary", InstallExecutable.class)
        installUnbuildableTestBinaryTask.destinationDir = project.projectDir
        installUnbuildableTestBinaryTask.executable = project.file("executable2")

        def buildableBinary = BaseBinarySpec.create(TestSpec, "testBuildableBinary", project.services.get(Instantiator), taskFactory)
        buildableBinary.setNamingScheme(buildableNamingScheme)
        buildableBinary.tasks.add(installBuildableTestBinaryTask)
        buildableBinary.toolChain = mockToolChain(true)

        def unbuildableBinary = BaseBinarySpec.create(TestSpec, "testUnbuildableBinary", project.services.get(Instantiator), taskFactory)
        unbuildableBinary.setNamingScheme(unbuildableNamingScheme)
        unbuildableBinary.tasks.add(installUnbuildableTestBinaryTask)
        unbuildableBinary.toolChain = mockToolChain(false)

        project.binaries.add(buildableBinary)
        project.binaries.add(unbuildableBinary)

        ModelMap<Task> tasksModelMap = DomainObjectCollectionBackedModelMap.wrap(
            Task.class,
            project.tasks,
            taskFactory,
            new Task.Namer(),
            new Action<Task>() {
                @Override
                public void execute(Task task) {
                    project.tasks.add(task)
                }
            })

        project.task(LifecycleBasePlugin.CHECK_TASK_NAME)

        when:
        new NativeBinariesTestPlugin.Rules().createTestTasks(project.tasks, project.binaries)
        new NativeBinariesTestPlugin.Rules().attachBinariesToCheckLifecycle(tasksModelMap, project.binaries)

        then:
        def checkTask = project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
        checkTask dependsOn("runBuildableTestBinary")
        checkTask not(dependsOn("runUnbuildableTestBinary"))
    }

    def mockToolChain(available) {
        def toolChain = Mock(NativeToolChainInternal)
        toolChain.select(_) >> {
            def provider = Mock(PlatformToolProvider)
            provider.isAvailable() >> available
            return provider
        }
        return toolChain
    }

    static class TestSpec extends DefaultNativeTestSuiteBinarySpec {
        @Override
        NativeTestSuiteSpec getTestSuite() {
            return null
        }
    }
}
