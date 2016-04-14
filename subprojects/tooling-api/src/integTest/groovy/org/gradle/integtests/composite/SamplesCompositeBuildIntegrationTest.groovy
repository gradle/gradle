/*
 * Copyright 2016 the original author or authors.
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


package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Ignore

import java.util.regex.Matcher

class SamplesCompositeBuildIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample sample = new Sample(temporaryFolder)

    @UsesSample('compositeBuild')
    def "can publish participants and resolve dependencies in non-integrated composite"() {
        given:
        tweakProject()

        when:
        executer.expectDeprecationWarning() // tapi on java 6
        executer.inDirectory(sample.dir)
        succeeds('publishAll')

        then:
        result.assertOutputContains("Running tasks [:uploadArchives] in participant: projectC")
        result.assertOutputContains("Running tasks [:b1:uploadArchives, :b2:uploadArchives] in participant: projectB")
        result.assertOutputContains("Running tasks [:uploadArchives] in participant: projectA")

        when:
        executer.expectDeprecationWarning() // tapi on java 6
        executer.inDirectory(sample.dir)
        succeeds('showDependencies')

        then:
        result.assertOutputContains("""
compile - Dependencies for source set 'main'.
+--- org.sample:b1:1.0
\\--- org.sample:b2:1.0
     \\--- org.sample:projectC:1.0
""")
    }

    @UsesSample('compositeBuild')
    def "can resolve participant dependencies in integrated composite"() {
        given:
        tweakProject()

        when:
        executer.expectDeprecationWarning() // tapi on java 6
        executer.inDirectory(sample.dir)
        executer.withArgument("-Pintegrated")
        succeeds('showDependencies')

        then:
        result.assertOutputContains("""
compile - Dependencies for source set 'main'.
+--- org.sample:b1:1.0 -> project projectB::b1
\\--- org.sample:b2:1.0 -> project projectB::b2
     \\--- org.sample:projectC:1.0 -> project projectC::
""")
    }

    @Ignore
    @UsesSample('compositeBuild')
    def "can build with dependencies in integrated composite"() {
        given:
        tweakProject()

        when:
        executer.inDirectory(sample.dir)
        executer.withArgument("-Pintegrated")
        succeeds('build')

        then:
        result.assertTasksExecuted(
            ":compositeBuild-projectC",
            ":projectC:compileJava",
            ":projectC:processResources",
            ":projectC:classes",
            ":projectC:jar",
            ":b2:compileJava",
            ":b2:processResources",
            ":b2:classes",
            ":b2:jar",
            ":b2:assemble")
    }

    private void tweakProject(TestFile projectDir = sample.dir) {
        // Inject some additional configuration into the sample build script
        def buildFile = projectDir.file('build.gradle')

        def buildContext = new IntegrationTestBuildContext()

        def gradleHomePath = Matcher.quoteReplacement(TextUtil.escapeString(buildContext.gradleHomeDir.absolutePath))
        def daemonBaseDirPath = Matcher.quoteReplacement(TextUtil.escapeString(buildContext.daemonBaseDir.absolutePath))
        def gradleUserHomePath = Matcher.quoteReplacement(TextUtil.escapeString(executer.gradleUserHomeDir.absolutePath))

        def buildScript = buildFile.text
        buildScript = buildScript.replaceAll("project\\.gradle\\.gradleHomeDir", "new File('${gradleHomePath}')")
        buildScript = buildScript.replaceFirst(
            "newGradleConnection\\(\\)",
            "newGradleConnection()" +
                ".useGradleUserHomeDir(new File('${gradleUserHomePath}'))" +
                ".daemonBaseDir(new File('${daemonBaseDirPath}'))" +
                ".daemonMaxIdleTime(10, java.util.concurrent.TimeUnit.SECONDS)"
        )
        buildFile.text = buildScript
    }
}
