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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.hamcrest.Matchers.containsString

class ValidatePluginsIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java-gradle-plugin"
        """
    }

    String iterableSymbol = '*'

    @Override
    String getNameSymbolFor(String name) {
        ".<name>"
    }

    @Override
    String getKeySymbolFor(String name) {
        '.<key>'
    }

    @Override
    void assertValidationSucceeds() {
        succeeds "validatePlugins"
    }

    @Override
    void assertValidationFailsWith(List<DocumentedProblem> messages) {
        fails "validatePlugins"
        def report = new TaskValidationReportFixture(file("build/reports/plugin-development/validation-report.txt"))
        report.verify(messages.collectEntries {
            def fullMessage = it.message
            if (!it.defaultDocLink) {
                fullMessage = "${fullMessage}\n${learnAt(it.id, it.section)}."
            }
            [(fullMessage): it.severity]
        })

        failure.assertHasCause "Plugin validation failed with ${messages.size()} problem${messages.size() > 1 ? 's' : ''}"
        messages.forEach { problem ->
            String indentedMessage = problem.message.replaceAll('\n', '\n    ').trim()
            failure.assertThatCause(containsString("$problem.severity: $indentedMessage"))
        }
    }

    @Override
    TestFile source(String path) {
        return file(path)
    }

    @ValidationTestFor(
        ValidationProblemId.MISSING_ANNOTATION
    )
    def "supports recursive types"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @Nested
                Tree tree

                public static class Tree {
                    @Optional @Nested
                    Tree left

                    @Optional @Nested
                    Tree right

                    String nonAnnotated
                }
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationMessage { type('MyTask').property('tree.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor(
        ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT
    )
    @Unroll
    def "task cannot have property with annotation @#ann.simpleName"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;

            public class MyTask extends DefaultTask {
                @${ann.simpleName}
                String getThing() {
                    return null;
                }

                @Nested
                Options getOptions() {
                    return null;
                }

                public static class Options {
                    @${ann.simpleName}
                    String getNestedThing() {
                        return null;
                    }
                }
            }
        """

        expect:
        assertValidationFailsWith([
            error(annotationInvalidInContext { annotation(ann.simpleName).type('MyTask').property('options.nestedThing').forTask() }, 'validation_problems', 'annotation_invalid_in_context'),
            error(annotationInvalidInContext { annotation(ann.simpleName).type('MyTask').property('thing').forTask() }, 'validation_problems', 'annotation_invalid_in_context')
        ])

        where:
        ann << [InputArtifact, InputArtifactDependencies]
    }

    @ValidationTestFor(
        ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION
    )
    def "can enable stricter validation"() {
        buildFile << """
            dependencies {
                implementation localGroovy()
            }
            def strictProp = providers.gradleProperty("strict").forUseAtConfigurationTime()
            validatePlugins.enableStricterValidation = strictProp.present
        """

        groovyTaskSource << """
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
        assertValidationSucceeds()

        when:
        file("gradle.properties").text = "strict=true"

        then:
        assertValidationFailsWith([
            error(missingNormalizationStrategy { type('MyTask').property('dirProp').annotatedWith('InputDirectory') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategy { type('MyTask').property('fileProp').annotatedWith('InputFile') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategy { type('MyTask').property('filesProp').annotatedWith('InputFiles') }, 'validation_problems', 'missing_normalization_annotation'),
        ])
    }

    def "can validate task classes using external types"() {
        buildFile << """
            ${mavenCentralRepository()}

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
                ${mavenCentralRepository()}
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

    @ValidationTestFor([
        ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT,
        ValidationProblemId.MISSING_ANNOTATION
    ])
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
                @PathSensitive(PathSensitivity.NONE)
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
        assertValidationFailsWith([
                error(missingAnnotationMessage { type('MyTransformAction').property('badTime').missingInput() }, 'validation_problems', 'missing_annotation'),
                error(annotationInvalidInContext { annotation('InputFile').type('MyTransformAction').property('inputFile').forTransformAction() }, 'validation_problems', 'annotation_invalid_in_context'),
                error(missingAnnotationMessage { type('MyTransformAction').property('oldThing').missingInput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor([
        ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT,
        ValidationProblemId.MISSING_ANNOTATION
    ])
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
                @PathSensitive(PathSensitivity.NONE)
                File getGoodFileInput();

                // Valid
                @Incremental
                @InputFiles
                @PathSensitive(PathSensitivity.NONE)
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
        assertValidationFailsWith([
            error(missingAnnotationMessage { type('MyTransformParameters').property('badTime').missingInput() }, 'validation_problems', 'missing_annotation'),
            error(incompatibleAnnotations { type('MyTransformParameters').property('incrementalNonFileInput').annotatedWith('Incremental').incompatibleWith('Input') }, 'validation_problems', 'incompatible_annotations'),
            error(annotationInvalidInContext { annotation('InputArtifact').type('MyTransformParameters').property('inputFile') }, 'validation_problems', 'annotation_invalid_in_context'),
            error(missingAnnotationMessage { type('MyTransformParameters').property('oldThing').missingInput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor(
        ValidationProblemId.MISSING_ANNOTATION
    )
    def "tests only classes from plugin source set"() {
        buildFile << """
            sourceSets {
                plugin {
                    java {
                        srcDir 'src/plugin/java'
                        compileClasspath = configurations.compileClasspath
                    }
                }
            }

            gradlePlugin {
                pluginSourceSet sourceSets.plugin
            }
        """

        file("src/main/java/MainTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MainTask extends DefaultTask {
                // WIll not be called out because it's in the main source set
                public long getBadProperty() {
                    return 0;
                }

                @TaskAction public void execute() {}
            }
        """

        file("src/plugin/java/PluginTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class PluginTask extends DefaultTask {
                // WIll be called out because it's among the plugin's sources
                public long getBadProperty() {
                    return 0;
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationMessage { type('PluginTask').property('badProperty').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }
}
