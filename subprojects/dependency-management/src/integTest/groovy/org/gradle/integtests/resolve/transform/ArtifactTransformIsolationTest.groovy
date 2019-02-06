/*
 * Copyright 2017 the original author or authors.
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
import spock.lang.IgnoreIf
/**
 * Ensures that artifact transform parameters are isolated from one another and the surrounding project state.
 */
class ArtifactTransformIsolationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """

def artifactType = Attribute.of('artifactType', String)

class Counter implements Serializable {    
    private int count = 0;

    public int increment() {
        return ++count;
    }

    public int getCount() {
        return count;
    }
}

public class CountRecorder extends ArtifactTransform {
    private final Counter counter;
    
    @javax.inject.Inject
    public CountRecorder(Counter counter) {
        this.counter = counter
        println "Creating CountRecorder"
    }
    
    List<File> transform(File input) {
        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.withWriter { out ->
            out.println String.valueOf(counter.getCount())
            for (int i = 0; i < 4; i++) {
                out.println String.valueOf(counter.increment())
            }
            out.close()
        }
        return [output]
    }
}
"""
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "serialized mutable class is isolated during artifact transformation"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()

        given:
        buildFile << """
            def counter = new Counter()

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            
            configurations {
                compile
            }
            
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }
            
            dependencies {
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'firstCount')
                    artifactTransform(CountRecorder) { params(counter) }
                }
                counter.increment()
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'secondCount')
                    artifactTransform(CountRecorder) { params(counter) }
                }
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'thirdCount')
                    artifactTransform(CountRecorder) { params(counter) }
                }
            }

            task resolveFirst(type: Copy) {
                def first = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'firstCount') }
                }.artifacts
                from first.artifactFiles
                into "\${buildDir}/libs1"
                
                doLast {
                    counter.increment()
                    println "files: " + first.collect { it.file.name }
                    println "ids: " + first.collect { it.id }
                    println "components: " + first.collect { it.id.componentIdentifier }
                    println "variants: " + first.collect { it.variant.attributes }
                }
            }
            
            task resolveSecond(type: Copy) {
                def second = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'secondCount') }
                }.artifacts
                from second.artifactFiles
                into "\${buildDir}/libs2"
                
                doLast {
                    counter.increment()
                    println "files: " + second.collect { it.file.name }
                    println "ids: " + second.collect { it.id }
                    println "components: " + second.collect { it.id.componentIdentifier }
                    println "variants: " + second.collect { it.variant.attributes }
                }
            }

            task resolveThird(type: Copy) {
                def third = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'thirdCount') }
                }.artifacts
                from third.artifactFiles
                into "\${buildDir}/libs3"
                
                doLast {
                    counter.increment()
                    println "files: " + third.collect { it.file.name }
                    println "ids: " + third.collect { it.id }
                    println "components: " + third.collect { it.id.componentIdentifier }
                    println "variants: " + third.collect { it.variant.attributes }
                }
            }
            
            task resolve dependsOn 'resolveFirst', 'resolveSecond', 'resolveThird'
        """

        when:
        run 'resolve'

        then:
        outputContains("variants: [{artifactType=firstCount}, {artifactType=firstCount}]")
        file("build/libs1").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs1/test-1.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]
        file("build/libs1/test2-2.3.jar.txt").readLines() == ["1", "2", "3", "4", "5"]

        and:
        outputContains("variants: [{artifactType=secondCount}, {artifactType=secondCount}]")
        file("build/libs2").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs2/test-1.3.jar.txt").readLines() == ["2", "3", "4", "5", "6"]
        file("build/libs2/test2-2.3.jar.txt").readLines() == ["2", "3", "4", "5", "6"]

        and:
        outputContains("variants: [{artifactType=thirdCount}, {artifactType=thirdCount}]")
        file("build/libs3").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs3/test-1.3.jar.txt").readLines() == ["3", "4", "5", "6", "7"]
        file("build/libs3/test2-2.3.jar.txt").readLines() == ["3", "4", "5", "6", "7"]

        and:
        output.count("Transforming") == 6
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 3
        output.count("Transforming test2-2.3.jar to test2-2.3.jar.txt") == 3
    }
}
