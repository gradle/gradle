/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.TextUtil
import org.junit.Rule
import spock.lang.IgnoreIf

@LeaksFileHandles
@IgnoreIf({ GradleContextualExecuter.embedded }) // These test run independent applications that connect to a Gradle distribution through the Tooling API
class SamplesToolingApiIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample sample = new Sample(temporaryFolder)

    @UsesSample('toolingApi/eclipse/groovy')
    def "can use tooling API to build Eclipse model"() {
        tweakProject()

        when:
        run()

        then:
        outputContains("gradle-tooling-api-")
        outputContains("src/main/java")
    }

    @UsesSample('toolingApi/runBuild/groovy')
    def "can use tooling API to run tasks"() {
        tweakProject()

        when:
        run()

        then:
        outputContains("Welcome to Gradle")
    }

    @UsesSample('toolingApi/idea/groovy')
    def "can use tooling API to build IDEA model"() {
        tweakProject()

        when:
        run()

        then:
        noExceptionThrown()
    }

    @UsesSample('toolingApi/model/groovy')
    def "can use tooling API to build general model"() {
        tweakProject()

        when:
        run()

        then:
        outputContains("Project: model")
        outputContains("    build")
    }

    @UsesSample('toolingApi/customModel/groovy')
    def "can use tooling API to register custom model"() {
        tweakPluginProject(sample.dir.file('plugin'))
        tweakProject(sample.dir.file('tooling'))

        when:
        run('publish', sample.dir.file('plugin'))
        run('run', sample.dir.file('tooling'))

        then:
        outputContains("   :a")
        outputContains("   :b")
        outputContains("   :c")
        noExceptionThrown()
    }

    private void tweakProject(File projectDir = sample.dir) {
        // Inject some additional configuration into the sample build script
        def buildFile = projectDir.file('build.gradle')
        def buildScript = buildFile.text
        def index = buildScript.indexOf('repositories {')
        assert index >= 0
        buildScript = buildScript.substring(0, index) + """
repositories {
    maven { url "${buildContext.localRepository.toURI()}" }
}
run {
    args = ["${TextUtil.escapeString(buildContext.gradleHomeDir.absolutePath)}", "${TextUtil.escapeString(executer.gradleUserHomeDir.absolutePath)}"]
    systemProperty 'org.gradle.daemon.idletimeout', 10000
    systemProperty 'org.gradle.daemon.registry.base', "${TextUtil.escapeString(projectDir.file("daemon").absolutePath)}"
}
""" + buildScript.substring(index)

        buildScript = buildScript.replace("def toolingApiVersion = gradle.gradleVersion", "def toolingApiVersion = \"${distribution.version.baseVersion.version}\"")

        buildFile.text = buildScript
    }

    private void tweakPluginProject(File projectDir) {
        // Inject some additional configuration into the sample build script
        def buildFile = projectDir.file('build.gradle')
        def buildScript = buildFile.text
        def index = buildScript.indexOf('publishing {')
        assert index >= 0
        buildScript = buildScript.substring(0, index) + """
repositories {
    maven { url "${buildContext.localRepository.toURI()}" }
}
""" + buildScript.substring(index)

        buildFile.text = buildScript
    }

    private ExecutionResult run(String task = 'run', File dir = sample.dir) {
        result = new GradleContextualExecuter(distribution, temporaryFolder, getBuildContext())
                .inDirectory(dir)
                .withTasks(task)
                .run()
    }
}
