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
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.hamcrest.Matchers
import org.jetbrains.annotations.NotNull
import spock.lang.IgnoreIf

import java.util.regex.Pattern

@IgnoreIf({ GradleContextualExecuter.parallel})
class ArtifactTransformWithDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'transform-deps'
            include 'common', 'lib', 'app'
        """
        mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.24").publish().allowAll()
        mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.25").publish().allowAll()
        mavenHttpRepo.module("junit", "junit", "4.11")
                .dependsOn("hamcrest", "hamcrest-core", "1.3")
                .publish().allowAll()
        mavenHttpRepo.module("hamcrest", "hamcrest-core", "1.3").publish().allowAll()

        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories {
        maven { url = '${mavenHttpRepo.uri}' }
    }
    configurations {
        implementation {
            canBeConsumed = false
            attributes.attribute(artifactType, 'jar')
        }
        "default" { extendsFrom implementation }
    }
    task producer(type: Producer) {
        outputFile = file("build/\${project.name}.jar")
    }
    artifacts {
        implementation producer.outputFile
    }

    dependencies {
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'size')
            artifactTransform(TestTransform) {
                params('Single step transform')
            }
        }

        // Multi step transform
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'intermediate')
            artifactTransform(TestTransform) {
                params('Transform step 1')
            }
        }
        registerTransform {
            from.attribute(artifactType, 'intermediate')
            to.attribute(artifactType, 'final')
            artifactTransform(TestTransform) {
                params('Transform step 2')
            }
        }

        //Multi step transform, without dependencies at step 1
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'middle')
            artifactTransform(SimpleTransform)
        }
        registerTransform {
            from.attribute(artifactType, 'middle')
            to.attribute(artifactType, 'end')
            artifactTransform(TestTransform) {
                params('Transform step 2')
            }
        }
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
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies

class Producer extends DefaultTask {
    @OutputFile
    RegularFileProperty outputFile = project.objects.fileProperty()

    @TaskAction
    def go() {
        outputFile.get().asFile.text = "output"
    }
}

class TestTransform extends ArtifactTransform {

    ArtifactTransformDependencies artifactDependencies
    String transformName

    @Inject
    TestTransform(String transformName, ArtifactTransformDependencies artifactDependencies) {
        this.transformName = transformName
        this.artifactDependencies = artifactDependencies
    }
    
    List<File> transform(File input) {
        println "\${transformName} received dependencies files \${artifactDependencies.files*.name} for processing \${input.name}"
        assert artifactDependencies.files.every { it.exists() }

        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
        return [output]
    }
}

class SimpleTransform extends ArtifactTransform {

    List<File> transform(File input) {
        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming without dependencies \${input.name} to \${output.name}"
        if (input.name == System.getProperty("failTransformOf")) {
            throw new RuntimeException("Cannot transform")
        }
        output.text = String.valueOf(input.length())
        return [output]
    }
}
"""
    }

    def "transform can access artifact dependencies as FileCollection when using ArtifactView"() {

        given:

        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(artifactType, 'size') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}
"""

        when:
        executer.withArgument("--parallel")
        run "resolve"

        then:
        output.count('Transforming') == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }

    def "transform can access artifact dependencies as FileCollection when using ArtifactView, even if first step did not use dependencies"() {

        given:

        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(artifactType, 'end') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}

"""

        when:
        executer.withArgument("--parallel")
        run "resolve"

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

    def "transform can access artifact dependencies, in previous transform step, as FileCollection when using ArtifactView"() {

        given:

        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(artifactType, 'final') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}

"""

        when:
        executer.withArgument("--parallel")
        run "resolve"

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

    def "transform can access artifact dependencies as FileCollection when using configuration attributes"() {

        given:

        buildFile << """
project(':app') {
    configurations {
        sizeConf {
            attributes.attribute(artifactType, 'size')
            extendsFrom implementation
        }
    }

    task resolve(type: Copy) {
        def artifacts = configurations.sizeConf.incoming.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}
"""

        when:
        executer.withArgument("--parallel")
        run "resolve"

        then:
        assertTransformationsExecuted(
            singleStep('common.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('lib.jar', 'slf4j-api-1.7.25.jar', 'common.jar'),
            singleStep('junit-4.11.jar', 'hamcrest-core-1.3.jar'),
            singleStep('slf4j-api-1.7.25.jar'),
        )
    }

    def "transform with changed dependencies are re-executed"() {
        given:
        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.implementation.incoming.artifactView {
            attributes { it.attribute(artifactType, 'size') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}
"""
        run "resolve", "-PuseOldDependencyVersion"

        when:
        run "resolve", "-PuseOldDependencyVersion", "--info"
        def outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 5
        outputLines.any { it ==~ /Skipping TestTransform: .*lib.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*slf4j-api-1.7.24.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 0

        when:
        run "resolve", "--info"
        outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 3
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 2
        outputLines.any { it ==~ /TestTransform: .*lib.jar is not up-to-date because:/ }
        outputLines.any { it ==~ /TestTransform: .*slf4j-api-1.7.25.jar is not up-to-date because:/ }
        assertTransformationsExecuted(
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('lib.jar','slf4j-api-1.7.25.jar', 'common.jar'),
        )
    }

    def "transforms with different dependencies in multiple dependency graphs are executed"() {
        given:
        mavenHttpRepo.module("org.slf4j", "slf4j-api", "1.7.26").publish().allowAll()
        settingsFile << "include('app2')"
        buildFile << """
            project(':app') {
                task resolveSize(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs/size"
                }
                task resolveInter(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'intermediate') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs/intermediate"
                }         
            }
            project(':app2') {
                task resolveSize(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs/size"
                }
                task resolveFinal(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'final') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs/final"
                }         
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
        run "resolveSize", "resolveInter", "resolveFinal"
        then:
        assertTransformationsExecuted(
            singleStep(common),
            singleStep(hamcrest),
            singleStep(junit411),

            singleStep(slf4jOld),
            singleStep(libWithSlf4Old),

            singleStep(slf4jNew),
            singleStep(libWithSlf4New),

            transformStep1(common),
            transformStep2(common),
            transformStep1(hamcrest),
            transformStep2(hamcrest),
            transformStep1(junit411),
            transformStep2(junit411),

            transformStep1(slf4jOld),
            transformStep1(libWithSlf4Old),

            transformStep1(slf4jNew),
            transformStep2(slf4jNew),

            transformStep1(libWithSlf4New),
            transformStep2(libWithSlf4New),
            // Looks like we execute this second step once with the first step having dependencies slf4j-api-1.7.26.jar and once with dependencies slf4j-api-1.7.25.jar
            // TODO wolfs: Schedule only the right transformations, not transformations with mixed dependencies which cannot be re-used by anything.
            transformStep2(libWithSlf4New)
        )

        def outputLines = output.readLines()
        def app1Resolve = outputLines.indexOf("> Task :app:resolveSize")
        def app2Resolve = outputLines.indexOf("> Task :app2:resolveSize")
        def libTransformWithOldSlf4j = outputLines.indexOf("Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar")
        def libTransformWithNewSlf4j = outputLines.indexOf("Single step transform received dependencies files [slf4j-api-1.7.26.jar, common.jar] for processing lib.jar")
        ![app1Resolve, app2Resolve, libTransformWithOldSlf4j, libTransformWithNewSlf4j].contains(-1)
        // scheduled transformation, executed before the resolve task
        assert libTransformWithOldSlf4j < app1Resolve
        // immediate transformation, we do not distinguish between transformations with different dependency graphs, so we only schedule the transformation once.
        // TODO wolfs: should be scheduled as well
        assert libTransformWithNewSlf4j > app2Resolve
    }

    def "transform does not execute when dependencies cannot be found"() {
        given:
        mavenHttpRepo.module("unknown", "not-found", "4.3").allowAll().assertNotPublished()
        buildFile << """
            project(':app') {
                task resolve(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }        
            
            project(':lib') {
                dependencies {
                    implementation "unknown:not-found:4.3"
                }
            }
        """

        when:
        fails "resolve"

        then:
        assertTransformationsExecuted()
        failure.assertResolutionFailure(":app:implementation")
        failure.assertThatCause(Matchers.containsString("Could not find unknown:not-found:4.3"))
    }

    def "transform does not execute when dependencies cannot be downloaded"() {
        given:
        def cantBeDownloaded = mavenHttpRepo.module("test", "cant-be-downloaded", "4.3").publish()
        cantBeDownloaded.pom.allowGetOrHead()
        cantBeDownloaded.artifact.expectDownloadBroken()

        buildFile << """
            project(':app') {
                task resolve(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }        
            
            project(':lib') {
                dependencies {
                    implementation "test:cant-be-downloaded:4.3"
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertResolutionFailure(":app:implementation")
        failure.assertThatCause(Matchers.containsString("Could not download cant-be-downloaded.jar (test:cant-be-downloaded:4.3)"))

        assertTransformationsExecuted(
            singleStep('common.jar'),
            singleStep('slf4j-api-1.7.25.jar'),
            singleStep('hamcrest-core-1.3.jar'),
            singleStep('junit-4.11.jar': ['hamcrest-core-1.3.jar'])
        )
    }

    def "transform does not execute when dependencies cannot be transformed"() {
        given:
        buildFile << """
            project(':app') {
                task resolve(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'end') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }        
        """

        when:
        fails "resolve", '-DfailTransformOf=slf4j-api-1.7.25.jar'

        then:
        failure.assertResolutionFailure(":app:implementation")
        failure.assertThatCause(Matchers.containsString("Failed to transform artifact 'slf4j-api.jar (org.slf4j:slf4j-api:1.7.25)'"))

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

    def "transform does not execute when task from dependencies fails"() {
        given:
        buildFile << """
            project(':app') {
                task resolve(type: Copy) {
                    def artifacts = configurations.implementation.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }        
            
            project(':common') {
                producer.doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        when:
        fails "resolve"

        then:
        assertTransformationsExecuted()
        failure.assertHasDescription("Execution failed for task ':common:producer'")
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
        int compareTo(@NotNull Transformation o) {
            name <=> o.name ?: artifact <=> o.artifact ?: Comparators.lexicographical(Comparator.<String>naturalOrder()).compare(dependencies, o.dependencies)
        }
    }
}
