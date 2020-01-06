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

package org.gradle.integtests.resolve.transform

import com.google.common.collect.Comparators
import com.google.common.collect.ImmutableSortedMultiset
import com.google.common.collect.Iterables
import com.google.common.collect.Multiset
import groovy.transform.Canonical
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.hamcrest.CoreMatchers
import spock.lang.IgnoreIf
import spock.lang.Unroll

import javax.annotation.Nonnull
import java.util.regex.Pattern

@IgnoreIf({ GradleContextualExecuter.parallel})
class ArtifactTransformWithDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest implements ArtifactTransformTestFixture {

    def setup() {
        settingsFile << """
            rootProject.name = 'transform-deps'
            include 'common', 'lib', 'app'
        """
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.24")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.25")).publish().allowAll()
        withColorVariants(mavenHttpRepo.module("junit", "junit", "4.11"))
                .dependsOn("hamcrest", "hamcrest-core", "1.3")
                .publish()
                .allowAll()
        withColorVariants(mavenHttpRepo.module("hamcrest", "hamcrest-core", "1.3")).publish().allowAll()
    }

    void setupBuildWithNoSteps(@DelegatesTo(Builder) Closure cl = {}) {
        setupBuildWithColorAttributes(cl)
        buildFile << """

allprojects {
    repositories {
        maven {
            url = '${mavenHttpRepo.uri}'
            metadataSources { gradleMetadata() }
        }
    }

    dependencies {
        artifactTypes {
            jar {
                attributes.attribute(color, 'blue')
            }
        }
    }

    task resolveGreen(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(color, 'green') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/green"
    }
}

project(':common') {
}

project(':lib') {
    dependencies {
        if (rootProject.hasProperty("useOldDependencyVersion")) {
            implementation 'org.slf4j:slf4j-api:1.7.24'
        } else {
            implementation 'org.slf4j:slf4j-api:1.7.25'
        }
        implementation project(':common')
    }
}

project(':app') {
    dependencies {
        implementation 'junit:junit:4.11'
        implementation project(':lib')
    }
}

import javax.inject.Inject

abstract class TestTransform implements TransformAction<Parameters> {
    interface Parameters extends TransformParameters {
        @Input
        String getTransformName()
        void setTransformName(String name)
    }

    @InputArtifactDependencies
    abstract FileCollection getInputArtifactDependencies()

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        println "\${parameters.transformName} received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"
        assert inputArtifactDependencies.every { it.exists() }

        def output = outputs.file(input.name + ".txt")
        def workspace = output.parentFile
        assert workspace.directory && workspace.list().length == 0
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
    }
}

abstract class SimpleTransform implements TransformAction<TransformParameters.None> {

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        def output = outputs.file(input.name + ".txt")
        def workspace = output.parentFile
        assert workspace.directory && workspace.list().length == 0
        println "Transforming without dependencies \${input.name} to \${output.name}"
        if (input.name == System.getProperty("failTransformOf")) {
            throw new RuntimeException("Cannot transform")
        }
        output.text = String.valueOf(input.length())
    }
}
"""
    }

    void setupBuildWithSingleStep() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        registerTransform(TestTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Single step transform'
            }
        }
    }
}
"""
    }

    void setupBuildWithFirstStepThatDoesNotUseDependencies() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        //Multi step transform, without dependencies at step 1
        registerTransform(SimpleTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'yellow')
        }
        registerTransform(TestTransform) {
            from.attribute(color, 'yellow')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Transform step 2'
            }
        }
    }
}
"""
    }

    void setupBuildWithTwoSteps() {
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        // Multi step transform
        registerTransform(TestTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'yellow')
            parameters {
                transformName = 'Transform step 1'
            }
        }
        registerTransform(TestTransform) {
            from.attribute(color, 'yellow')
            to.attribute(color, 'green')
            parameters {
                transformName = 'Transform step 2'
            }
        }
    }
}
"""
    }

    @ToBeFixedForInstantExecution
    def "transform can access artifact dependencies as a set of files when using ArtifactView"() {
        given:
        setupBuildWithSingleStep()

        when:
        executer.withArgument("--parallel")
        run ":app:resolveGreen"

        then:
        output.count('Transforming') == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }

    def "transform can access file dependencies as a set of files when using ArtifactView"() {
        given:
        setupBuildWithSingleStep()
        buildFile << """
project(':common') {
    dependencies {
        implementation files("otherLib.jar")
    }
}
"""

        when:
        executer.withArgument("--parallel")
        run "common:resolveGreen"

        then:
        output.count('Transforming') == 1
        output.contains('Single step transform received dependencies files [] for processing otherLib.jar')
    }

    def "transform can access artifact dependencies as a set of files when using ArtifactView, even if first step did not use dependencies"() {
        given:
        setupBuildWithFirstStepThatDoesNotUseDependencies()

        when:
        executer.withArgument("--parallel")
        run "app:resolveGreen"

        then:
        assertTransformationsExecuted(
            simpleTransform('common.jar'),
            simpleTransform('hamcrest-core-1.3.jar'),
            simpleTransform('lib.jar'),
            simpleTransform('junit-4.11.jar'),
            simpleTransform('slf4j-api-1.7.25.jar'),
            transformStep2('common.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep2('slf4j-api-1.7.25.jar')
        )
    }

    @ToBeFixedForInstantExecution
    def "transform can access artifact dependencies, in previous transform step, as set of files when using ArtifactView"() {
        given:
        setupBuildWithTwoSteps()

        when:
        executer.withArgument("--parallel")
        run "app:resolveGreen"

        then:
        assertTransformationsExecuted(
            transformStep1('common.jar'),
            transformStep1('hamcrest-core-1.3.jar'),
            transformStep1('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep1('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep1('slf4j-api-1.7.25.jar'),
            transformStep2('common.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            transformStep2('slf4j-api-1.7.25.jar')
        )
    }

    @ToBeFixedForInstantExecution
    def "transform with changed set of dependencies are re-executed"() {
        given:
        setupBuildWithSingleStep()

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PuseOldDependencyVersion"

        then: // new version, should run
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.24.jar'),
            singleStep('lib.jar','slf4j-api-1.7.24.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PuseOldDependencyVersion"

        then: // no changes, should be up-to-date
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        assertTransformationsExecuted()
    }

    @ToBeFixedForInstantExecution
    def "transform with changed project file dependencies content or path are re-executed"() {
        given:
        setupBuildWithSingleStep()

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out"

        then: // new path, should re-run
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // new name, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar", "-PcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()
    }

    @ToBeFixedForInstantExecution
    def "can attach @PathSensitive(NONE) to dependencies property"() {
        given:
        setupBuildWithNoSteps()
        buildFile << """
allprojects {
    dependencies {
        registerTransform(NoneTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}

abstract class NoneTransform implements TransformAction<TransformParameters.None> {
    @InputArtifactDependencies @PathSensitive(PathSensitivity.NONE)
    abstract FileCollection getInputArtifactDependencies()

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        println "Single step transform received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"

        def output = outputs.file(input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
    }
}

"""

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out"

        then: // new path, should skip consumer
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // new name, should skip consumer
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar", "-PcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common-blue.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def "can attach @#classpathAnnotation.simpleName to dependencies property"() {
        given:
        setupBuildWithNoSteps {
            produceJars()
        }
        buildFile << """
allprojects {
    dependencies {
        registerTransform(ClasspathTransform) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
        }
    }
}

abstract class ClasspathTransform implements TransformAction<TransformParameters.None> {
    @InputArtifactDependencies @${classpathAnnotation.simpleName}
    abstract FileCollection getInputArtifactDependencies()

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        println "Single step transform received dependencies files \${inputArtifactDependencies*.name} for processing \${input.name}"

        def output = outputs.file(input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
    }
}

"""

        when:
        run ":app:resolveGreen"

        then:
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('common.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )

        when:
        run ":app:resolveGreen"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out"

        then: // new path, should skip consumer
        result.assertTasksNotSkipped(":common:producer")
        assertTransformationsExecuted(
            singleStep('common.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // new name, should skip consumer
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
        )

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar"

        then: // no changes, should be up-to-date
        result.assertTasksNotSkipped()
        assertTransformationsExecuted()

        when:
        run ":app:resolveGreen", "-PcommonOutputDir=out", "-PcommonFileName=common-blue.jar", "-PcommonContent=new"

        then: // new content, should re-run
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted(
            singleStep('common-blue.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common-blue.jar')
        )

        when:
        run ":app:resolveGreen"

        then: // have seen these inputs before
        result.assertTasksNotSkipped(":common:producer", ":app:resolveGreen")
        assertTransformationsExecuted()

        where:
        classpathAnnotation << [Classpath, CompileClasspath]
    }

    @ToBeFixedForInstantExecution
    def "transforms with different dependencies in multiple dependency graphs are executed"() {
        given:
        withColorVariants(mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.26")).publish().allowAll()
        settingsFile << "include('app2')"
        setupBuildWithTwoSteps()
        buildFile << """
            project(':app2') {
                dependencies {
                    implementation 'junit:junit:4.11'
                    implementation 'org.slf4j:slf4j-api:1.7.26'
                    implementation project(':lib')
                }
            }
        """
        def hamcrest = 'hamcrest-core-1.3.jar'
        def junit411 = ['junit-4.11.jar': [hamcrest]]
        def common = 'common.jar'
        def slf4jOld = 'slf4j-api-1.7.25.jar'
        def slf4jNew = 'slf4j-api-1.7.26.jar'
        def libWithSlf4Old = ['lib.jar': [slf4jOld, common]]
        def libWithSlf4New = ['lib.jar': [slf4jNew, common]]

        when:
        run ":app:resolveGreen", ":app2:resolveGreen"

        then:
        assertTransformationsExecuted(
            transformStep1(common),
            transformStep2(common),
            transformStep1(hamcrest),
            transformStep2(hamcrest),
            transformStep1(junit411),
            transformStep2(junit411),

            transformStep1(slf4jOld),
            transformStep2(slf4jOld),

            transformStep1(libWithSlf4Old),
            transformStep2(libWithSlf4Old),

            transformStep1(slf4jNew),
            transformStep2(slf4jNew),

            transformStep1(libWithSlf4New),
            transformStep2(libWithSlf4New),
        )

        def outputLines = output.readLines()
        def app1Resolve = outputLines.indexOf("> Task :app:resolveGreen")
        def app2Resolve = outputLines.indexOf("> Task :app2:resolveGreen")
        def libTransformWithOldSlf4j = outputLines.indexOf("Transform step 1 received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar")
        def libTransformWithNewSlf4j = outputLines.indexOf("Transform step 1 received dependencies files [slf4j-api-1.7.26.jar, common.jar] for processing lib.jar")
        ![app1Resolve, app2Resolve, libTransformWithOldSlf4j, libTransformWithNewSlf4j].contains(-1)
        // scheduled transformations, executed before the resolve task
        assert libTransformWithOldSlf4j < app1Resolve
        assert libTransformWithNewSlf4j < app2Resolve
    }

    @ToBeFixedForInstantExecution
    def "transform does not execute when dependencies cannot be found"() {
        given:
        mavenHttpRepo.module("unknown", "not-found", "4.3").allowAll().assertNotPublished()
        setupBuildWithTwoSteps()
        buildFile << """
            project(':lib') {
                dependencies {
                    implementation "unknown:not-found:4.3"
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        assertTransformationsExecuted()
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Could not find unknown:not-found:4.3"))
    }

    @ToBeFixedForInstantExecution
    def "transform does not execute when dependencies cannot be downloaded"() {
        given:
        def cantBeDownloaded = withColorVariants(mavenHttpRepo.module("test", "cant-be-downloaded", "4.3")).publish()
        cantBeDownloaded.moduleMetadata.allowGetOrHead()
        cantBeDownloaded.artifact.expectDownloadBroken()
        setupBuildWithTwoSteps()

        buildFile << """
            project(':lib') {
                dependencies {
                    implementation "test:cant-be-downloaded:4.3"
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Could not download cant-be-downloaded-4.3.jar (test:cant-be-downloaded:4.3)"))

        assertTransformationsExecuted(
            transformStep1('common.jar'),
            transformStep1('slf4j-api-1.7.25.jar'),
            transformStep1('hamcrest-core-1.3.jar'),
            transformStep1('junit-4.11.jar': ['hamcrest-core-1.3.jar']),
            transformStep2('common.jar'),
            transformStep2('slf4j-api-1.7.25.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            transformStep2('junit-4.11.jar': ['hamcrest-core-1.3.jar'])
        )
    }

    @ToBeFixedForInstantExecution
    def "transform does not execute when dependencies cannot be transformed"() {
        given:
        setupBuildWithFirstStepThatDoesNotUseDependencies()

        when:
        fails ":app:resolveGreen", '-DfailTransformOf=slf4j-api-1.7.25.jar'

        then:
        failure.assertResolutionFailure(":app:implementation")
        failure.assertHasFailures(1)
        failure.assertThatCause(CoreMatchers.containsString("Failed to transform slf4j-api-1.7.25.jar (org.slf4j:slf4j-api:1.7.25)"))

        assertTransformationsExecuted(
            simpleTransform('common.jar'),
            transformStep2('common.jar'),
            simpleTransform('hamcrest-core-1.3.jar'),
            transformStep2('hamcrest-core-1.3.jar'),
            simpleTransform('junit-4.11.jar'),
            transformStep2('junit-4.11.jar', 'hamcrest-core-1.3.jar'),

            simpleTransform('slf4j-api-1.7.25.jar'),
            simpleTransform('lib.jar'),
        )
    }

    @ToBeFixedForInstantExecution
    def "transform does not execute when dependencies cannot be built"() {
        given:
        setupBuildWithTwoSteps()
        buildFile << """
            project(':common') {
                producer.doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails ":app:resolveGreen"

        then:
        assertTransformationsExecuted()
        failure.assertHasDescription("Execution failed for task ':common:producer'")
        failure.assertHasFailures(1)
        failure.assertHasCause("broken")
    }

    Transformation simpleTransform(String artifact) {
        return new Transformation("SimpleTransform", artifact, [])
    }

    Transformation transformStep1(String artifact, String... dependencies) {
        return transformStep1((artifact): (dependencies as List))
    }

    Transformation transformStep1(Map<String, List<String>> artifactWithDependencies) {
        return Transformation.fromMap("Transform step 1", artifactWithDependencies)
    }

    Transformation singleStep(String artifact, String... dependencies) {
        return singleStep((artifact): (dependencies as List))
    }

    Transformation singleStep(Map<String, List<String>> artifactWithDependencies) {
        Transformation.fromMap("Single step transform", artifactWithDependencies)
    }

    Transformation transformStep2(String artifact, String... dependencies) {
        return transformStep2((artifact): (dependencies as List))
    }

    Transformation transformStep2(Map<String, List<String>> artifactWithDependencies) {
        return Transformation.fromMap("Transform step 2", artifactWithDependencies.collectEntries { artifact, dependencies -> [(artifact + ".txt"): dependencies.collect { it + ".txt"}]})
    }

    void assertTransformationsExecuted(Transformation... expectedTransforms) {
        assertTransformationsExecuted(ImmutableSortedMultiset.<Transformation>copyOf(expectedTransforms as List))
    }

    void assertTransformationsExecuted(Multiset<Transformation> expectedTransforms) {
        def transforms = executedTransformations()
        assert ImmutableSortedMultiset.copyOf(transforms) == expectedTransforms
    }

    List<Transformation> executedTransformations() {
        def withDependenciesPattern = Pattern.compile(/(.*) received dependencies files \[(.*)] for processing (.*)/)
        def simpleTransformPattern = Pattern.compile(/Transforming without dependencies (.*) to (.*)/)
        output.readLines().collect {
            def withDependenciesMatcher = withDependenciesPattern.matcher(it)
            if (withDependenciesMatcher.matches()) {
                return new Transformation(withDependenciesMatcher.group(1), withDependenciesMatcher.group(3), withDependenciesMatcher.group(2).empty ? [] : withDependenciesMatcher.group(2).split(", ").toList())
            }
            def simpleTransformMatcher = simpleTransformPattern.matcher(it)
            if (simpleTransformMatcher.matches()) {
                return new Transformation("SimpleTransform", simpleTransformMatcher.group(1), [])
            }
            return null
        }.findAll()
    }

    @Canonical
    static class Transformation implements Comparable<Transformation> {

        static Transformation fromMap(String name, Map<String, List<String>> artifactWithDependencies) {
            Iterables.getOnlyElement(artifactWithDependencies.entrySet()).with { entry -> new Transformation(name, entry.key, entry.value) }
        }

        Transformation(String name, String artifact, List<String> dependencies) {
            this.name = name
            this.artifact = artifact
            this.dependencies = dependencies
        }

        final String name
        final String artifact
        final List<String> dependencies

        @Override
        String toString() {
            "${name} - ${artifact} (${dependencies})"
        }

        @Override
        int compareTo(@Nonnull Transformation o) {
            name <=> o.name ?: artifact <=> o.artifact ?: Comparators.lexicographical(Comparator.<String>naturalOrder()).compare(dependencies, o.dependencies)
        }
    }
}
