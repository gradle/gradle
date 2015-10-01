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

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.NativeTestSuiteSpec
import org.gradle.nativeplatform.test.internal.DefaultNativeTestSuiteBinarySpec
import org.gradle.nativeplatform.test.tasks.RunTestExecutable
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.util.TestUtil
import spock.lang.Specification

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

        def binary = BaseBinarySpec.create(NativeTestSuiteBinarySpec, TestSpec, "testBinary", project.services.get(Instantiator), Mock(ITaskFactory))
        binary.setNamingScheme(namingScheme)
        binary.tasks.add(task)

        project.binaries.add(binary)

        when:
        new NativeBinariesTestPlugin.Rules().createTestTasks(project.tasks, project.binaries)

        then:
        binary.tasks.withType(RunTestExecutable).size() == 1
        binary.tasks.run != null

        and:
        project.tasks.getByName("check") dependsOn("runTestBinary")
    }

    static class TestSpec extends DefaultNativeTestSuiteBinarySpec {
        @Override
        NativeTestSuiteSpec getTestSuite() {
            return null
        }
    }
}
