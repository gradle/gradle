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
package org.gradle.ide.visualstudio

import org.gradle.ide.visualstudio.fixtures.AbstractVisualStudioIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires([
    UnitTestPreconditions.CanInstallExecutable,
    UnitTestPreconditions.NotMacOs
])
class NativeIdeSamplesIntegrationTest extends AbstractVisualStudioIntegrationSpec {
    @Rule public final Sample visualStudio = sample(temporaryFolder, 'visual-studio')

    private static Sample sample(TestDirectoryProvider testDirectoryProvider, String name) {
        return new Sample(testDirectoryProvider, "native-binaries/${name}/groovy", name)
    }

    @ToBeFixedForConfigurationCache
    def "visual studio"() {
        given:
        sample visualStudio

        when:
        run "visualStudio"

        then:
        final solutionFile = solutionFile(visualStudio.dir.file("vs/visual-studio.sln"))
        solutionFile.assertHasProjects("mainExe", "helloDll", "helloLib")
        solutionFile.content.contains "GlobalSection(SolutionNotes) = postSolution"
        solutionFile.content.contains "Text2 = The projects in this solution are [helloDll, helloLib, mainExe]."

        final dllProjectFile = projectFile(visualStudio.dir.file("vs/helloDll.vcxproj"))
        dllProjectFile.projectXml.PropertyGroup.find({it.'@Label' == 'Custom'}).ProjectDetails[0].text() == "Project is named helloDll"

        final libProjectFile = projectFile(visualStudio.dir.file("vs/helloLib.vcxproj"))
        libProjectFile.projectXml.PropertyGroup.find({it.'@Label' == 'Custom'}).ProjectDetails[0].text() == "Project is named helloLib"
    }

    @Requires(UnitTestPreconditions.HasMsBuild)
    @ToBeFixedForConfigurationCache
    def "build generated visual studio solution"() {
        useMsbuildTool()

        given:
        sample visualStudio
        run "visualStudio"

        when:
        def resultDebug = msbuild
            .withWorkingDir(visualStudio.dir)
            .withSolution(solutionFile(visualStudio.dir.file("vs/visual-studio.sln")))
            .withConfiguration("debug")
            .withProject("mainExe")
            .succeeds()

        then:
        resultDebug.size() == 1
        resultDebug[0].assertTasksExecuted(':compileMainExecutableMainCpp', ':linkMainExecutable', ':mainExecutable', ':installMainExecutable', ':compileHelloSharedLibraryHelloCpp', ':linkHelloSharedLibrary', ':helloSharedLibrary')
        installation(visualStudio.dir.file('build/install/main')).assertInstalled()
    }
}
