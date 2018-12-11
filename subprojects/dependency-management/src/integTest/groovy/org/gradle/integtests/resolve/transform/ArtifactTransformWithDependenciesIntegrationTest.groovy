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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.hamcrest.Matchers
import spock.lang.IgnoreIf

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
        output.count('Transforming') == 10
        output.contains('Transform step 2 received dependencies files [slf4j-api-1.7.25.jar.txt, common.jar.txt] for processing lib.jar.txt')
        output.contains('Transform step 2 received dependencies files [hamcrest-core-1.3.jar.txt] for processing junit-4.11.jar.txt')
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
        output.count('Transforming') == 10
        output.contains('Transform step 1 received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Transform step 2 received dependencies files [slf4j-api-1.7.25.jar.txt, common.jar.txt] for processing lib.jar.txt')
        output.contains('Transform step 1 received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
        output.contains('Transform step 2 received dependencies files [hamcrest-core-1.3.jar.txt] for processing junit-4.11.jar.txt')
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
        output.count("Transforming") == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
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
        outputLines.any { it == "Single step transform received dependencies files [] for processing slf4j-api-1.7.25.jar" }
        outputLines.any { it ==~ /TestTransform: .*slf4j-api-1.7.25.jar is not up-to-date because:/ }
        outputLines.any { it == "Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar" }
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

        when:
        run "resolveSize", "resolveInter", "resolveFinal"
        then:
        output.count("Transforming") == 20
        [
            'common.jar': [],
            'hamcrest-core-1.3.jar': [],
            'junit-4.11.jar': ['hamcrest-core-1.3.jar']
        ].each { artifact, dependencies ->
            assert output.contains("Single step transform received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
            assert output.contains("Transform step 1 received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
            assert output.contains("Transform step 2 received dependencies files [${dependencies.collect { it + ".txt" }.join(", ")}] for processing ${artifact}.txt")
        }
        [
            'slf4j-api-1.7.25.jar': [],
            'lib.jar': ['slf4j-api-1.7.25.jar', 'common.jar']
        ].each { artifact, dependencies ->
            assert output.contains("Single step transform received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
            assert output.contains("Transform step 1 received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
        }
        [
            'slf4j-api-1.7.26.jar': [],
            'lib.jar': ['slf4j-api-1.7.26.jar', 'common.jar']
        ].each { artifact, dependencies ->
            assert output.contains("Single step transform received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
            assert output.contains("Transform step 1 received dependencies files [${dependencies.join(", ")}] for processing ${artifact}")
            assert output.contains("Transform step 2 received dependencies files [${dependencies.collect { it + ".txt" }.join(", ")}] for processing ${artifact}.txt")
        }

        // TODO wolfs: Looks like we execute this second step once with the first step having dependencies slf4j-api-1.7.26.jar and once with dependencies slf4j-api-1.7.25.jar
        output.count("Transform step 2 received dependencies files [slf4j-api-1.7.26.jar.txt, common.jar.txt] for processing lib.jar.txt") == 2

        def outputLines = output.readLines()
        def app1Resolve = outputLines.indexOf("> Task :app:resolveSize")
        def app2Resolve = outputLines.indexOf("> Task :app2:resolveSize")
        def lib1Transform = outputLines.indexOf("Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar")
        def lib2Transform = outputLines.indexOf("Single step transform received dependencies files [slf4j-api-1.7.26.jar, common.jar] for processing lib.jar")
        ![app1Resolve, app2Resolve, lib1Transform, lib2Transform].contains(-1)
        assert lib1Transform < app1Resolve // scheduled
        assert lib2Transform > app2Resolve // immediate, TODO wolfs: should be scheduled as well
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
        output.count('Transforming') == 0
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
        output.count('Transforming') == 4
        failure.assertResolutionFailure(":app:implementation")
        failure.assertThatCause(Matchers.containsString("Could not download cant-be-downloaded.jar (test:cant-be-downloaded:4.3)"))
        output.contains("Single step transform received dependencies files [] for processing common.jar")
        output.contains("Single step transform received dependencies files [] for processing slf4j-api-1.7.25.jar")
        output.contains("Single step transform received dependencies files [] for processing hamcrest-core-1.3.jar")
        output.contains("Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar")
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
        output.count('Transforming') == 8
        failure.assertResolutionFailure(":app:implementation")
        failure.assertThatCause(Matchers.containsString("Failed to transform artifact 'slf4j-api.jar (org.slf4j:slf4j-api:1.7.25)'"))
        ['lib.jar', 'common.jar', 'hamcrest-core-1.3.jar', 'junit-4.11.jar', 'slf4j-api-1.7.25.jar'].each {
            assert output.contains("Transforming without dependencies ${it} to ${it}.txt")
        }
        [
                'common.jar': [],
                'hamcrest-core-1.3.jar': [],
                'junit-4.11.jar': ['hamcrest-core-1.3.jar'],
        ].each { artifact, dependencies ->
            assert output.contains("Transform step 2 received dependencies files [${dependencies.collect { it + '.txt'}.join(', ')}] for processing ${artifact}.txt")
        }
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
        output.count('Transforming') == 0
        failure.assertHasDescription("Execution failed for task ':common:producer'")
        failure.assertHasCause("broken")
    }

}
