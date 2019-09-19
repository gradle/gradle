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

package org.gradle.plugin.devel.tasks

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class ValidatePluginsIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java-gradle-plugin"

            dependencies {
                implementation gradleApi()
            }
        """
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "validatePlugins"
    }

    @Override
    void assertValidationFailsWith(Map<String, Severity> messages) {
        fails "validatePlugins"

        def expectedReportContents = messages
            .collect { message, severity ->
                "$severity: $message"
            }
            .join("\n")
        assert file("build/reports/plugin-development/validation-report.txt").text == expectedReportContents

        failure.assertHasCause "Plugin validation failed"
        messages.forEach { message, severity ->
            failure.assertHasCause("$severity: $message")
        }
    }

    @Override
    TestFile source(String path) {
        return file(path)
    }

    @Unroll
    def "task cannot have property with annotation @#annotation.simpleName"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;

            public class MyTask extends DefaultTask {
                @${annotation.simpleName}
                String getThing() {
                    return null;
                }

                @Nested
                Options getOptions() { 
                    return null;
                }

                public static class Options {
                    @${annotation.simpleName}
                    String getNestedThing() {
                        return null;
                    }
                }
            }
        """

        expect:
        fails("validatePlugins")
        failure.assertHasDescription("Execution failed for task ':validatePlugins'.")
        failure.assertHasCause("Plugin validation failed. See")
        failure.assertHasCause("Error: Type 'MyTask': property 'thing' is annotated with invalid property type @${annotation.simpleName}.")
        failure.assertHasCause("Error: Type 'MyTask': property 'options.nestedThing' is annotated with invalid property type @${annotation.simpleName}.")

        reportFileContents() == """
            Error: Type 'MyTask': property 'options.nestedThing' is annotated with invalid property type @${annotation.simpleName}.
            Error: Type 'MyTask': property 'thing' is annotated with invalid property type @${annotation.simpleName}.
            """.stripIndent().trim()

        where:
        annotation << [InputArtifact, InputArtifactDependencies]
    }

    def "can enable stricter validation"() {
        buildFile << """
            apply plugin: "groovy"

            dependencies {
                implementation localGroovy()
            }
            
            validatePlugins.enableStricterValidation = project.hasProperty('strict')
        """
        file("src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @InputFile
                File fileProp
                
                @InputFiles
                Set<File> filesProp

                @InputDirectory
                File dirProp

                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver
            }
        """

        expect:
        succeeds("validatePlugins")

        when:
        fails "validatePlugins", "-Pstrict"

        then:
        reportFileContents() == """
            Warning: Type 'MyTask': property 'dirProp' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.
            Warning: Type 'MyTask': property 'fileProp' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.
            Warning: Type 'MyTask': property 'filesProp' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.
            """.stripIndent().trim()
    }

    def "can validate task classes using external types"() {
        buildFile << """
            ${jcenterRepository()}

            dependencies {
                implementation 'com.typesafe:config:1.3.2'
            }
        """

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import java.io.File;
            import com.typesafe.config.Config;

            public class MyTask extends DefaultTask {
                @Input
                public long getGoodTime() {
                    return 0;
                }
                
                @Optional @Input
                public Config getConfig() { return null; } 
                
                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationSucceeds()
    }

    def "can validate task classes using types from other projects"() {
        settingsFile << """
            include 'lib'
        """

        buildFile << """  
            allprojects {
                ${jcenterRepository()}
            }

            project(':lib') {
                apply plugin: 'java'

                dependencies {
                    implementation 'com.typesafe:config:1.3.2'
                }
            }          

            dependencies {
                implementation project(':lib')
            }
        """

        source("lib/src/main/java/MyUtil.java") << """
            import com.typesafe.config.Config;

            public class MyUtil {
                public Config getConfig() {
                    return null;
                }
            }
        """

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MyTask extends DefaultTask {
                @Input
                public long getGoodTime() {
                    return 0;
                }
                
                @Input
                public MyUtil getUtil() { return new MyUtil(); }
                
                @TaskAction public void execute() {} 
            }
        """

        expect:
        assertValidationSucceeds()
    }

    def "can validate properties of an artifact transform action"() {
        file("src/main/java/MyTransformAction.java") << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;
            import java.io.*;

            public abstract class MyTransformAction implements TransformAction {
                // Should be ignored because it's not a getter
                public void getVoid() {
                }

                // Should be ignored because it's not a getter
                public int getWithParameter(int count) {
                    return count;
                }

                // Ignored because static
                public static int getStatic() {
                    return 0;
                }

                // Ignored because injected
                @javax.inject.Inject
                public abstract org.gradle.api.internal.file.FileResolver getInjected();

                // Valid because it is annotated
                @InputArtifact
                public abstract Provider<FileSystemLocation> getGoodInput();

                // Invalid because it has no annotation
                public long getBadTime() {
                    return System.currentTimeMillis();
                }

                // Invalid because it has some other annotation
                @Deprecated
                public String getOldThing() {
                    return null;
                }
                
                // Unsupported annotation
                @InputFile
                public abstract File getInputFile();
            }
        """

        expect:
        fails "validatePlugins"
        failure.assertHasCause "Plugin validation failed"
        failure.assertHasCause "Error: Type 'MyTransformAction': property 'badTime' is not annotated with an input annotation."
        failure.assertHasCause "Error: Type 'MyTransformAction': property 'inputFile' is annotated with invalid property type @InputFile."
        failure.assertHasCause "Error: Type 'MyTransformAction': property 'oldThing' is not annotated with an input annotation."

        reportFileContents() == """
            Error: Type 'MyTransformAction': property 'badTime' is not annotated with an input annotation.
            Error: Type 'MyTransformAction': property 'inputFile' is annotated with invalid property type @InputFile.
            Error: Type 'MyTransformAction': property 'oldThing' is not annotated with an input annotation.
            """.stripIndent().trim()
    }

    def "can validate properties of an artifact transform parameters object"() {
        file("src/main/java/MyTransformParameters.java") << """
            import org.gradle.api.*;
            import org.gradle.api.file.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;
            import org.gradle.work.*;
            import java.io.*;

            public interface MyTransformParameters extends TransformParameters {
                // Should be ignored because it's not a getter
                void getVoid();

                // Should be ignored because it's not a getter
                int getWithParameter(int count);

                // Ignored because injected
                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver getInjected();

                // Valid because it is annotated
                @InputFile
                File getGoodFileInput();

                // Valid
                @Incremental
                @InputFiles
                FileCollection getGoodIncrementalInput();

                // Valid
                @Input
                String getGoodInput();
                void setGoodInput(String value);

                // Invalid because only file inputs can be incremental
                @Incremental
                @Input
                String getIncrementalNonFileInput();
                void setIncrementalNonFileInput(String value);

                // Invalid because it has no annotation
                long getBadTime();

                // Invalid because it has some other annotation
                @Deprecated
                String getOldThing();
                
                // Unsupported annotation
                @InputArtifact
                File getInputFile();
            }
        """

        expect:
        fails "validatePlugins"
        failure.assertHasCause "Plugin validation failed"
        failure.assertHasCause "Error: Type 'MyTransformParameters': property 'badTime' is not annotated with an input annotation."
        failure.assertHasCause "Error: Type 'MyTransformParameters': property 'incrementalNonFileInput' is annotated with @Incremental that is not allowed for @Input properties."
        failure.assertHasCause "Error: Type 'MyTransformParameters': property 'inputFile' is annotated with invalid property type @InputArtifact."
        failure.assertHasCause "Error: Type 'MyTransformParameters': property 'oldThing' is not annotated with an input annotation."

        reportFileContents() == """
            Error: Type 'MyTransformParameters': property 'badTime' is not annotated with an input annotation.
            Error: Type 'MyTransformParameters': property 'incrementalNonFileInput' is annotated with @Incremental that is not allowed for @Input properties.
            Error: Type 'MyTransformParameters': property 'inputFile' is annotated with invalid property type @InputArtifact.
            Error: Type 'MyTransformParameters': property 'oldThing' is not annotated with an input annotation.
            """.stripIndent().trim()
    }

    def "can run old task"() {
        executer.expectDeprecationWarning()

        when:
        run "validateTaskProperties"
        then:
        executedAndNotSkipped(":validatePlugins")
        output.contains("The validateTaskProperties task has been deprecated. This is scheduled to be removed in Gradle 7.0. Please use the validatePlugins task instead.")
    }

    private String reportFileContents() {
        file("build/reports/plugin-development/validation-report.txt").text
    }
}
