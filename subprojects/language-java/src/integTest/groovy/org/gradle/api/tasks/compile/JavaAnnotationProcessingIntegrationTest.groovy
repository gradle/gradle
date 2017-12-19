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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.fixtures.AnnotationProcessorFixture

class JavaAnnotationProcessingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def annotationProcessorProjectDir = testDirectory.file("annotation-processor").createDir()

        settingsFile << """
            include "annotation-processor"
        """
        buildFile << """
            apply plugin: 'java'
            
            configurations {
                annotationProcessor
            }
            
            dependencies {
                compile project(":annotation-processor")
                annotationProcessor project(":annotation-processor")
            }
            
            compileJava {
                // Use forking to work around javac's jar cache
                options.fork = true
                options.annotationProcessorPath = configurations.annotationProcessor
            }
        """

        annotationProcessorProjectDir.file("build.gradle") << """
            apply plugin: "java"
        """

        def fixture = new AnnotationProcessorFixture()

        // A library class used by processor at runtime, but not the generated classes
        fixture.writeSupportLibraryTo(annotationProcessorProjectDir)

        // The processor and annotation
        fixture.writeApiTo(annotationProcessorProjectDir)
        fixture.writeAnnotationProcessorTo(annotationProcessorProjectDir)

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
            compileJava.options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
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
                List<String> getAsArguments() {
                    ["-Amessage=\${message}".toString()]
                }
            }
            
            compileJava.options.addCompilerArgumentProvider(new HelperAnnotationProcessor("fromOptions"))
        """

        when:
        run "compileJava"

        then:
        file("build/classes/java/main/TestAppHelper.java").text == 'class TestAppHelper {    String getValue() { return "fromOptions"; }}'
    }
}
