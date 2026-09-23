/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.cli.CommandLineParser
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AgentOutputLocationTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def userHome = tmpDir.file("user-home")
    def rootDir = tmpDir.file("root")

    def "uses the default project cache directory of the build"() {
        given:
        rootDir.file("settings.gradle").createFile()

        expect:
        isOutputFileIn(resolve("--project-dir", rootDir.absolutePath), rootDir.file(".gradle"))
    }

    def "uses the root directory when run from a subproject"() {
        given:
        rootDir.file("settings.gradle").createFile()
        def subDir = rootDir.createDir("sub")

        expect:
        isOutputFileIn(resolve("--project-dir", subDir.absolutePath), rootDir.file(".gradle"))
    }

    def "uses the project cache directory given on the command line"() {
        given:
        rootDir.file("settings.gradle").createFile()
        def cacheDir = tmpDir.file("custom-cache")

        expect:
        isOutputFileIn(resolve("--project-dir", rootDir.absolutePath, "--project-cache-dir", cacheDir.absolutePath), cacheDir)
    }

    def "uses the project cache directory given as a property"() {
        given:
        rootDir.file("settings.gradle").createFile()
        def cacheDir = tmpDir.file("custom-cache")

        expect:
        isOutputFileIn(resolve("--project-dir", rootDir.absolutePath, "-Dorg.gradle.projectcachedir=${cacheDir.absolutePath}"), cacheDir)
    }

    def "does not use the root directory when there is no build definition"() {
        given:
        def emptyDir = tmpDir.createDir("empty")
        def buildLayoutFactory = Stub(BuildLayoutFactory) {
            getLayoutFor(_) >> Stub(BuildLayout) {
                getRootDirectory() >> emptyDir
                isBuildDefinitionMissing() >> true
            }
        }

        when:
        def location = resolve(buildLayoutFactory, "--project-dir", emptyDir.absolutePath)

        then:
        def projectCacheDir = location.parentFile.parentFile.parentFile.parentFile
        projectCacheDir.parentFile == userHome.file("undefined-build")
        isOutputFileIn(location, projectCacheDir)
    }

    def "uses a different directory for each invocation"() {
        given:
        rootDir.file("settings.gradle").createFile()

        when:
        def first = resolve("--project-dir", rootDir.absolutePath)
        def second = resolve("--project-dir", rootDir.absolutePath)

        then:
        first.parentFile != second.parentFile
        first.parentFile.parentFile == second.parentFile.parentFile
    }

    private static boolean isOutputFileIn(File location, File projectCacheDir) {
        assert location.name == "build-output.log"
        assert location.parentFile.name ==~ /[a-z2-7]{26}/
        assert location.parentFile.parentFile == new File(projectCacheDir, "agent/builds")
        true
    }

    private File resolve(String... args) {
        resolve(new BuildLayoutFactory(), args)
    }

    private File resolve(BuildLayoutFactory buildLayoutFactory, String... args) {
        def location = new AgentOutputLocation(buildLayoutFactory)
        def initialPropertiesConverter = new InitialPropertiesConverter()
        def buildLayoutConverter = new BuildLayoutConverter()
        def propertiesConverter = new LayoutToPropertiesConverter(new BuildLayoutFactory())

        def parser = new CommandLineParser()
        initialPropertiesConverter.configure(parser)
        buildLayoutConverter.configure(parser)
        location.configure(parser)

        def commandLine = parser.parse(args)
        def initialProperties = initialPropertiesConverter.convert(commandLine)
        def buildLayout = buildLayoutConverter.convert(initialProperties, commandLine, null) {
            it.gradleUserHomeDir = userHome // don't use the default
        }
        def properties = propertiesConverter.convert(initialProperties, buildLayout)

        return location.resolve(commandLine, properties.properties, buildLayout)
    }
}
