/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.ProjectSourceSet
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.internal.configure.TestNativeBinariesFactory
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultNativeExecutableBinarySpecTest extends Specification {
    def instantiator = DirectInstantiator.INSTANCE
    def namingScheme = new DefaultBinaryNamingScheme("bigOne", "executable", [])
    def taskFactory = Mock(ITaskFactory)
    def tasks = new DefaultNativeExecutableBinarySpec.DefaultTasksCollection(new DefaultBinaryTasksCollection(null, taskFactory))

    def "has useful string representation"() {
        given:
        def executable = BaseComponentFixtures.create(DefaultNativeExecutableSpec, new ModelRegistryHelper(), new DefaultComponentSpecIdentifier("path", "name"), Stub(ProjectSourceSet), instantiator)

        when:
        def binary = TestNativeBinariesFactory.create(DefaultNativeExecutableBinarySpec, namingScheme.getLifecycleTaskName(), instantiator, taskFactory, executable, namingScheme,
            Mock(NativeDependencyResolver), Stub(NativePlatform), Stub(BuildType), new DefaultFlavor("flavorOne"))

        then:
        binary.toString() == "executable 'bigOne:executable'"
    }

    def "returns null for link and install when none defined"() {
        expect:
        tasks.link == null
        tasks.install == null
    }

    def "returns link task when defined"() {
        when:
        final linkTask = TestUtil.createTask(LinkExecutable)
        tasks.add(linkTask)

        then:
        tasks.link == linkTask
    }

    def "returns install task when defined"() {
        when:
        final install = TestUtil.createTask(InstallExecutable)
        tasks.add(install)

        then:
        tasks.install == install
    }
}
