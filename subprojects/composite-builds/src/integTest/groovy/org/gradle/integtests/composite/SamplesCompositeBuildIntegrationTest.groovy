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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

class SamplesCompositeBuildIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final Sample sample = new Sample(temporaryFolder)

    def setup() {
        requireGradleDistribution()
    }

    @Unroll
    @UsesSample('compositeBuilds/basic')
    @ToBeFixedForInstantExecution
    def "can run app with command-line composite with #dsl dsl"() {
        given:
        executer.withRepositoryMirrors()

        when:
        executer.inDirectory(sample.dir.file("$dsl/my-app")).withArguments("--include-build", "../my-utils")
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":run"
        outputContains("The answer is 42")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('compositeBuilds/basic')
    @ToBeFixedForInstantExecution
    def "can run app when modified to be a composite with #dsl dsl"() {
        given:
        executer.withRepositoryMirrors()

        when:
        executer.inDirectory(sample.dir.file("$dsl/my-app"))
            .withArguments("--settings-file", "settings-composite.$extension")
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":run"
        outputContains("The answer is 42")

        where:
        dsl      | extension
        'groovy' | 'gradle'
        'kotlin' | 'gradle.kts'
    }

    @Unroll
    @UsesSample('compositeBuilds/basic')
    @ToBeFixedForInstantExecution
    def "can run app when included in a composite with #dsl dsl"() {
        given:
        executer.withRepositoryMirrors()

        when:
        executer.inDirectory(sample.dir.file("$dsl/composite"))
        succeeds(':run')

        then:
        executed ":my-utils:number-utils:jar", ":my-utils:string-utils:jar", ":my-app:run", ":run"
        outputContains("The answer is 42")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('compositeBuilds/hierarchical-multirepo')
    @ToBeFixedForInstantExecution
    def "can run app in hierarchical composite with #dsl dsl"() {
        given:
        executer.withRepositoryMirrors()

        when:
        executer.inDirectory(sample.dir.file("$dsl"))
        succeeds(':run')

        then:
        executed ":number-utils:jar", ":string-utils:jar", ":run"
        outputContains("The answer is 42")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('compositeBuilds/hierarchical-multirepo')
    @ToBeFixedForInstantExecution
    def "can publish locally and remove submodule from hierarchical composite with #dsl dsl"() {
        given:
        def multiRepoAppDir = sample.dir.file("$dsl")
        executer.withRepositoryMirrors()

        when:
        executer.inDirectory(multiRepoAppDir)
        succeeds(':publishDeps')

        then:
        executed ":number-utils:publishIvyPublicationToIvyRepository", ":string-utils:publishIvyPublicationToIvyRepository"

        and:
        multiRepoAppDir.file('../local-repo/org.sample/number-utils/1.0')
            .assertContainsDescendants("ivy-1.0.xml", "number-utils-1.0.jar")
        multiRepoAppDir.file('../local-repo/org.sample/string-utils/1.0')
            .assertContainsDescendants("ivy-1.0.xml", "string-utils-1.0.jar")

        when:
        multiRepoAppDir.file("modules/string-utils").deleteDir()

        and:
        executer.inDirectory(multiRepoAppDir)
        succeeds(":run")

        then:
        executed ":number-utils:jar", ":run"
        notExecuted ":string-utils:jar"
        outputContains("The answer is 42")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('compositeBuilds/plugin-dev')
    @ToBeFixedForInstantExecution
    def "can develop plugin with composite with dsl #dsl"() {
        when:
        executer.inDirectory(sample.dir.file("$dsl/consumer")).withArguments("--include-build", "../greeting-plugin")
        succeeds('greeting')

        then:
        executed ":greeting-plugin:jar", ":greeting"
        outputContains("Hi Bob!!!")

        when:
        def greetingTaskSource = sample.dir.file("$dsl/greeting-plugin/src/main/java/org/sample/GreetingTask.java")
        greetingTaskSource.replace("Hi", "G'day")

        and:
        executer.inDirectory(sample.dir.file("$dsl/consumer")).withArguments("--include-build", "../greeting-plugin")
        succeeds('greeting')

        then:
        executed ":greeting-plugin:jar", ":greeting"
        outputContains("G'day Bob!!!")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('compositeBuilds/declared-substitution')
    @ToBeFixedForInstantExecution
    def "can include build with declared substitution with #dsl dsl"() {
        given:
        def myAppDir = sample.dir.file("$dsl/my-app")

        when:
        executer.inDirectory(myAppDir)
            .withArguments("--settings-file", "settings-without-declared-substitution.$extension")
        fails(':run')

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':run'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':runtimeClasspath'.")
        failure.assertHasCause("Cannot resolve external dependency org.sample:number-utils:1.0 because no repositories are defined.")

        when:
        executer.inDirectory(myAppDir)
        succeeds(':run')

        then:
        executed ":anonymous-library:jar", ":run"
        outputContains("The answer is 42")

        where:
        dsl      | extension
        'groovy' | 'gradle'
        'kotlin' | 'gradle.kts'
    }
}
