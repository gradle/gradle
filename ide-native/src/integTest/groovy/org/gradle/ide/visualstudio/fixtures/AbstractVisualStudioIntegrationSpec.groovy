/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.ide.visualstudio.fixtures

import org.gradle.ide.fixtures.IdeCommandLineUtil
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec

abstract class AbstractVisualStudioIntegrationSpec extends AbstractInstalledToolChainIntegrationSpec {
    final def projectConfigurations = ['debug', 'release'] as Set

    protected static String filePath(String... paths) {
        return filePath(paths as List)
    }

    protected static String filePath(Iterable<String> paths) {
        return paths.join(';')
    }

    void useMsbuildTool() {
        // msbuild requires a Gradle distribution to execute the gradle/gradlew script.
        //
        // We isolate the Gradle daemons so we know the daemons are only used for these particular tests.
        //
        // To workaround a msbuild issue, we require the daemon to be used by the test executor, so the Gradle daemon will be
        // started before msbuild is executed.
        // If msbuild starts the Gradle daemon, it will wait for the Gradle daemon to exit before moving on to the next msbuild task.
        // When this happens, it seems like tests using msbuild are slow or hanging (>10 minutes instead of 30 seconds).
        // The msbuild behavior occurs because we capture the stdout/stderr from Gradle instead of letting msbuild collect it and fork the daemon child process.
        // We direct the output because msbuild jumbles up stdout/stderr in ways that mess up our output scraping.
        // There is probably another way we could redirect the output or scrape the output, but this is good enough for testing.
        // In real builds (we do not redirect output normally), msbuild and VS do not hang like this.
        executer.requireIsolatedDaemons().requireDaemon()

        initScript << IdeCommandLineUtil.generateGradleProbeInitFile('visualStudio', 'msbuild')
        initScript << """
            allprojects { p ->
                p.plugins.withType(VisualStudioPlugin.class) {
                    p.tasks.withType(GenerateProjectFileTask) {
                        doFirst {
                            def relativeToRoot = org.gradle.util.internal.RelativePathUtil.relativePath(visualStudioProject.projectFile.location.parentFile, rootProject.projectDir).replaceAll('/', '\\\\\\\\')
                            if (relativeToRoot == "") {
                                relativeToRoot = "."
                            }
                            visualStudioProject.projectFile.withXml { xml ->
                                redirectOutputForAll xml.asNode().PropertyGroup.findAll { it.'@Label' == 'NMakeConfiguration' }, relativeToRoot
                            }
                        }
                    }
                }
            }

            def redirectOutputForAll(nodes, relativeToRoot) {
                nodes.each { node ->
                    redirectOutput node.NMakeBuildCommandLine[0], relativeToRoot
                    redirectOutput node.NMakeCleanCommandLine[0], relativeToRoot
                    redirectOutput node.NMakeReBuildCommandLine[0], relativeToRoot
                }
            }

            def redirectOutput(Node node, String relativeToRoot) {
                String value = node.value()
                node.value = '''
For /f "tokens=1-3 delims=/: " %%a in ("%TIME%") do (if %%a LSS 10 (set timestamp=0%%a%%b%%c) else (set timestamp=%%a%%b%%c))
set timestamp=%timestamp:~0,6%
''' + "set outputDir=\${relativeToRoot}\\\\output\\\\%timestamp%" + '''
md %outputDir%
set outputLog=%outputDir%\\\\output.txt
set errorLog=%outputDir%\\\\error.txt
echo %outputLog%
''' + value + ' 1>%outputLog% 2>%errorLog%'
            }
        """
    }

    File getHostGradleWrapperFile() {
        if (OperatingSystem.current().isWindows()) {
            return file('gradlew.bat')
        }
        return file('gradlew')
    }

    protected MSBuildExecutor getMsbuild() {
        // Gradle needs to be isolated so that msbuild does not leave behind daemons
        assert executer.distribution.gradleHomeDir != null
        assert !executer.usesSharedDaemons()
        def executer = new MSBuildExecutor(testDirectory, toolChain)
        executer.withArgument('/p:Platform=Win32')
        return executer
    }

    protected SolutionFile solutionFile(String path) {
        return solutionFile(file(path))
    }

    protected SolutionFile solutionFile(File file) {
        return new SolutionFile(file)
    }

    protected ProjectFile projectFile(String path) {
        return projectFile(file(path))
    }

    protected ProjectFile projectFile(File file) {
        return new ProjectFile(file)
    }

    protected FiltersFile filtersFile(String path) {
        return filtersFile(file(path))
    }

    protected FiltersFile filtersFile(File file) {
        return new FiltersFile(file)
    }
}
