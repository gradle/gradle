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


package org.gradle.buildinit.plugins

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class WrapperPluginSpec extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())
    def project = TestUtil.createRootProject(testDir.testDirectory)

    def "adds 'wrapper' task"() {
        when:
        project.pluginManager.apply WrapperPlugin

        then:
        project.tasks.wrapper instanceof Wrapper
        project.tasks.wrapper.group == "Build Setup"
    }
}
