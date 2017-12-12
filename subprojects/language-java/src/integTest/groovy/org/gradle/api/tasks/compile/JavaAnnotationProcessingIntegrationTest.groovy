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

package org.gradle.api.tasks.compile

import org.gradle.api.internal.tasks.compile.AnnotationProcessorDetector
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.fixtures.AnnotationProcessorFixture

class JavaAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def annotationProjectDir = file("annotation")
        def processorProjectDir = file("processor")

        settingsFile << """
            include "annotation"
            include "processor"
        """

        buildFile << """
            apply plugin: 'java'
            
            configurations {
                annotationProcessor
            }
            
            compileJava {
                // Use forking to work around javac's jar cache
                options.fork = true
            }
        """

        annotationProjectDir.file("build.gradle") << """
            apply plugin: "java"
        """

        processorProjectDir.file("build.gradle") << """
            apply plugin: "java"
            dependencies {
                compile project(':annotation')
            }
        """

        def fixture = new AnnotationProcessorFixture()

        // A library class used by processor at runtime, but not the generated classes
        fixture.writeSupportLibraryTo(processorProjectDir)

        // The processor and annotation
        fixture.writeApiTo(annotationProjectDir)
        fixture.writeAnnotationProcessorTo(processorProjectDir)

        // The class that is the target of the processor
        file('src/main/java/TestApp.java') << '''
            @Helper
            class TestApp { 
                public static void main(String[] args) {
                    System.out.println(new TestAppHelper().getValue()); // generated class
                }
            }
        '''
    }

    def "can specify generated source directory"() {
        buildFile << """
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            
            compileJava {
                options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
                options.annotationProcessorPath = configurations.annotationProcessor
            }
        """

        expect:
        succeeds "compileJava"
        file("build/generated-sources/TestAppHelper.java").text == 'class TestAppHelper {    String getValue() { return "greetings"; }}'
    }

    def "can model annotation processor arguments"() {
        buildFile << """                                                       
            import org.gradle.api.tasks.compile.CompilerArgumentProvider

            class HelperAnnotationProcessor implements CompilerArgumentProvider {
                @Input
                String message
                
                HelperAnnotationProcessor(String message) {
                    this.message = message
                }
                
                @Override
                List<String> asArguments() {
                    ["-Amessage=\${message}".toString()]
                }
            }
            
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            
            compileJava {
                options.compilerArgumentProviders << new HelperAnnotationProcessor("fromOptions")
                options.annotationProcessorPath = configurations.annotationProcessor
            }
        """

        when:
        run "compileJava"

        then:
        file("build/classes/java/main/TestAppHelper.java").text == 'class TestAppHelper {    String getValue() { return "fromOptions"; }}'
    }

    def "processorpath is respected even when specified from compilerArgs, but deprecation warning is emitted"() {
        buildFile << """
            configurations {
                processor
            }
            
            dependencies {
                compileOnly project(":annotation")
                processor project(":processor")
            }
            
            compileJava {
                inputs.files configurations.processor
                options.compilerArgs += [ "-processorpath", configurations.processor.asPath ]
            }
        """

        when:
        result = executer.expectDeprecationWarning().withTasks('compileJava').run()

        then:
        file('build/classes/java/main/TestAppHelper.class').exists()
        result.output.contains(AnnotationProcessorDetector.PROCESSOR_PATH_DEPRECATION_MESSAGE)
    }

}
