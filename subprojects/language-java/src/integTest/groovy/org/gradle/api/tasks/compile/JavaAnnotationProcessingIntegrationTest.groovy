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

import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorPathFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.fixtures.HelperProcessorFixture
import spock.lang.Issue

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

        def fixture = new HelperProcessorFixture()

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
            compileJava.options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
        """

        expect:
        succeeds "compileJava"
        file("build/generated-sources/TestAppHelper.java").text == 'class TestAppHelper {    String getValue() { return "greetings"; }}'
    }

    def "generated sources are cleaned up on full compilations"() {
        given:
        buildFile << """
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            compileJava.options.annotationProcessorGeneratedSourcesDirectory = file("build/generated-sources")
        """
        succeeds "compileJava"

        when:
        buildFile << """compileJava.options.annotationProcessorPath = files()"""
        fails("compileJava")

        then:
        file("build/generated-sources/TestAppHelper.java").assertDoesNotExist()
    }

    def "can model annotation processor arguments"() {
        buildFile << """                                                       
            class HelperAnnotationProcessor implements CommandLineArgumentProvider {
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
            
            compileJava.options.compilerArgumentProviders << new HelperAnnotationProcessor("fromOptions")
        """

        when:
        run "compileJava"

        then:
        file("build/classes/java/main/TestAppHelper.java").text == 'class TestAppHelper {    String getValue() { return "fromOptions"; }}'
    }

    def "processors in the compile classpath are respected, but deprecation warning is emitted"() {

        buildFile << """
            dependencies {
                compile project(":processor")
            }
        """

        when:
        result = executer.expectDeprecationWarning().withTasks('compileJava').run()

        then:
        file('build/classes/java/main/TestAppHelper.class').exists()
        outputContains(AnnotationProcessorPathFactory.COMPILE_CLASSPATH_DEPRECATION_MESSAGE)
    }

    def "empty processor path overrides processors in the compile classpath, and no deprecation warning is emitted"() {
        buildFile << """
            dependencies {
                compile project(":processor")
            }
            
            compileJava {
              options.annotationProcessorPath = files()
            }
        """

        file('src/main/java/TestApp.java').text = '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        '''

        expect:
        succeeds "compileJava"
        !file('build/classes/java/main/TestAppHelper.class').exists()
    }

    def "empty custom processor configuration overrides processors in the compile classpath, and no deprecation warning is emitted"() {
        buildFile << """
            configurations {
                apt
            }
            
            dependencies {
                compile project(":processor")
            }
            
            compileJava {
              options.annotationProcessorPath = configurations.apt
            }
        """

        file('src/main/java/TestApp.java').text = '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        '''

        expect:
        succeeds "compileJava"
        !file('build/classes/java/main/TestAppHelper.class').exists()
    }


    def "processors in the compile classpath don't emit deprecation warning if processing is disabled"() {
        buildFile << """
            dependencies {
                compile project(":processor")
            }
            compileJava {
              options.compilerArgs << "-proc:none"
            }
        """

        file('src/main/java/TestApp.java').text = '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                    System.out.println("Hello world!");
                }
            }
        '''

        expect:
        succeeds "compileJava"
        !file('build/classes/java/main/TestAppHelper.class').exists()
    }

    def "processorpath is respected even when specified from compilerArgs, but deprecation warning is emitted"() {
        buildFile << """
            configurations {
                processor
            }
            
            dependencies {
                compile project(":annotation")
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
        outputContains(AnnotationProcessorPathFactory.PROCESSOR_PATH_DEPRECATION_MESSAGE)
    }

    def "explicit -processor option overrides automatic detection"() {
        buildFile << """
            
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            compileJava.options.compilerArgs << "-processor" << "unknown.Processor"
        """

        expect:
        fails("compileJava")
        failure.assertHasErrorOutput("Annotation processor 'unknown.Processor' not found")
    }

    @Issue("https://github.com/gradle/gradle/issues/5448")
    def "can add classes directory as source"() {
        // This is sometimes done for IDE support.
        // We should deprecate this behaviour, since output directories are added as inputs.
        buildFile << """
            dependencies {
                compileOnly project(":annotation")
                annotationProcessor project(":processor")
            }
            sourceSets.main.java.srcDir("build/classes/java/main")
        """

        expect:
        succeeds "compileJava"

        when:
        file('src/main/java/TestApp.java').text = '''
            @Helper
            class TestApp { 
                public static void main(String[] args) {
                    System.out.println(new TestAppHelper().getValue() + "Changed!"); // generated class
                }
            }
        '''
        then:
        succeeds "compileJava"
    }

}
