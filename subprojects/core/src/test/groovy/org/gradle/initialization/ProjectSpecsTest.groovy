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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.WrapUtil.toSet

@CleanupTestDirectory
class ProjectSpecsTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private final File settingsDir = temporaryFolder.createDir("settings")
    private final File rootProjectDir = temporaryFolder.createDir("settings/root")
    private final File otherDir = temporaryFolder.createDir("other")

    def "explicit project dir that is the build root selects the root project"() {
        given:
        StartParameter parameter = startParameter(settingsDir, settingsDir)

        when:
        def spec = ProjectSpecs.forStartParameter(parameter, settings())

        then:
        spec.selectProject("settings 'foo'", registry()) == root
    }

    def "explicit project dir that is not the build root must match a project"() {
        given:
        StartParameter parameter = startParameter(otherDir, otherDir)

        when:
        ProjectSpecs.forStartParameter(parameter, settings()).selectProject("settings 'foo'", registry())

        then:
        def e = thrown(InvalidUserDataException)
        e.message.startsWith("Project directory '$otherDir' is not part of the build defined by settings 'foo'.")
    }

    def "current dir that is the build root selects the root project"() {
        given:
        StartParameter parameter = startParameter(null, settingsDir)

        when:
        def spec = ProjectSpecs.forStartParameter(parameter, settings())

        then:
        spec.selectProject("settings 'foo'", registry()) == root
    }

    def "current dir that is not the build root must match a project"() {
        given:
        StartParameter parameter = startParameter(null, otherDir)

        when:
        ProjectSpecs.forStartParameter(parameter, settings()).selectProject("settings 'foo'", registry())

        then:
        thrown(InvalidUserDataException)
    }

    private static StartParameter startParameter(File projectDir, File currentDir) {
        StartParameter parameter = new StartParameterInternal()
        if (projectDir != null) {
            parameter.setProjectDir(projectDir)
        }
        parameter.setCurrentDir(currentDir)
        return parameter
    }

    private SettingsInternal settings() {
        return Stub(SettingsInternal) {
            getSettingsDir() >> settingsDir
        }
    }

    private ProjectDescriptorInternal root = Mock(ProjectDescriptorInternal) {
        getProjectDir() >> rootProjectDir
    }

    private ProjectDescriptorRegistry registry() {
        return Stub(ProjectDescriptorRegistry) {
            getAllProjects() >> toSet(root)
            getRootProject() >> root
        }
    }
}
