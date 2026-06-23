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

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultNativeExecutableBinarySpecTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    final testUtil = TestUtil.create(tmpDir)
    def namingScheme = DefaultBinaryNamingScheme.component("bigOne").withBinaryType("executable")
    def tasks = new DefaultNativeExecutableBinarySpec.DefaultTasksCollection(new DefaultBinaryTasksCollection(null, null, CollectionCallbackActionDecorator.NOOP))

    def "has useful string representation"() {
        given:
        def executable = BaseComponentFixtures.createNode(NativeExecutableSpec, DefaultNativeExecutableSpec, new DefaultComponentSpecIdentifier("path", "name"))

        when:
        def binary = TestNativeBinariesFactory.create(NativeExecutableBinarySpec, DefaultNativeExecutableBinarySpec, namingScheme.getBinaryName(), executable, namingScheme,
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
        final linkTask = testUtil.task(LinkExecutable)
        tasks.add(linkTask)

        then:
        tasks.link == linkTask
    }

    def "returns install task when defined"() {
        when:
        final install = testUtil.task(InstallExecutable)
        tasks.add(install)

        then:
        tasks.install == install
    }
}
