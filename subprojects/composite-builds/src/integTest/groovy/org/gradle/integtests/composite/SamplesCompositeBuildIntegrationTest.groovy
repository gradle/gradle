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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Ignore

@Ignore
class SamplesCompositeBuildIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule public final Sample sample = new Sample(temporaryFolder)

    @UsesSample('compositeBuilds/basic')
    def "can run app with command-line composite"() {
        when:
        executer.inDirectory(sample.dir.file("my-app")).withArguments("--include-build", "../my-utils")
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":run"
        outputContains("The answer is 42")
    }

    @UsesSample('compositeBuilds/basic')
    def "can run app when modified to be a composite"() {
        when:
        executer.inDirectory(sample.dir.file("my-app")).withArguments("--settings-file", "settings-composite.gradle")
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":run"
        outputContains("The answer is 42")
    }

    @UsesSample('compositeBuilds/basic')
    def "can run app when included in a composite"() {
        when:
        executer.inDirectory(sample.dir.file("composite"))
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":my-app:run", ":run"
        outputContains("The answer is 42")
    }

    @UsesSample('compositeBuilds/hierarchical-multirepo')
    def "can run app in hierarchical composite"() {
        when:
        executer.inDirectory(sample.dir.file("multirepo-app"))
        succeeds(':run')

        then:
        executed ":number-utils:jar", ":string-utils:jar", ":run"
        outputContains("The answer is 42")
    }

    @UsesSample('compositeBuilds/hierarchical-multirepo')
    def "can publish locally and remove submodule from hierarchical composite"() {
        when:
        executer.inDirectory(sample.dir.file("multirepo-app"))
        succeeds(':publishDeps')

        then:
        executed ":number-utils:uploadArchives", ":string-utils:uploadArchives"
        sample.dir.file('local-repo/org.sample/number-utils/1.0').assertContainsDescendants("ivy-1.0.xml", "number-utils-1.0.jar")
        sample.dir.file('local-repo/org.sample/string-utils/1.0').assertContainsDescendants("ivy-1.0.xml", "string-utils-1.0.jar")

        when:
        sample.dir.file("multirepo-app/modules/string-utils").deleteDir()

        and:
        executer.inDirectory(sample.dir.file("multirepo-app"))
        succeeds(":run")

        then:
        executed ":number-utils:jar", ":run"
        notExecuted ":string-utils:jar"
        outputContains("The answer is 42")
    }

    @UsesSample('compositeBuilds/plugin-dev')
    def "can develop plugin with composite"() {
        when:
        executer.inDirectory(sample.dir.file("consumer")).withArguments("--include-build", "../greeting-plugin")
        succeeds(':greetBob')

        then:
        executed ":greeting-plugin:jar", ":greetBob"
        outputContains("Hi Bob!!!")

        when:
        def greetingTaskSource = sample.dir.file("greeting-plugin/src/main/java/org/sample/GreetingTask.java")
        greetingTaskSource.text = greetingTaskSource.text.replace("Hi", "G'day")

        and:
        executer.inDirectory(sample.dir.file("consumer")).withArguments("--include-build", "../greeting-plugin")
        succeeds(':greetBob')

        then:
        executed ":greeting-plugin:jar", ":greetBob"
        outputContains("G'day Bob!!!")
    }

    @UsesSample('compositeBuilds/declared-substitution')
    def "can include build with declared substitution"() {
        when:
        executer.inDirectory(sample.dir.file("my-app"))
            .withArguments("--settings-file", "settings-without-declared-substitution.gradle")
        fails(':run')

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':run'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':runtimeClasspath'.")
        failure.assertHasCause("Cannot resolve external dependency org.sample:number-utils:1.0 because no repositories are defined.")

        when:
        executer.inDirectory(sample.dir.file("my-app"))
        succeeds(':run')

        then:
        executed ":anonymous-library:jar", ":run"
        outputContains("The answer is 42")
    }
}
