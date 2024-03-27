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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.getPluralEnding
import static org.hamcrest.Matchers.containsString
import static org.junit.Assume.assumeNotNull

class AbstractValidatePluginsIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    String iterableSymbol = '.*'

    def setup() {
        enableProblemsApiCheck()
        buildFile """
            apply plugin: "java-gradle-plugin"
        """
    }

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
        def report = new TaskValidationReportFixture(file("build/reports/plugin-development/validation-report.json"))
        report.verify(messages.collectEntries {
            def fullMessage = it.message
            if (!it.defaultDocLink) {
                fullMessage = "${fullMessage}\n${learnAt(it.id, it.section)}"
            }
            [(fullMessage): it.severity]
        })

        failure.assertHasCause "Plugin validation failed with ${messages.size()} problem${getPluralEnding(messages)}"
        messages.forEach { problem ->
            String indentedMessage = problem.message.replaceAll('\n', '\n    ').trim()
            failure.assertThatCause(containsString("$problem.severity: $indentedMessage"))
        }

        // TODO (donat) do probably don't want to have this, as the explicit problem assertions are preferred
        def problems = collectedProblems
        assert problems.size() == messages.size()
        problems.any { problem ->
            messages.any { message ->
                if (message.config) {
                    TextUtil.endLineWithDot(problem.definition.id.displayName) == message.config.label().toString()
                } else {
                    message.message.contains(TextUtil.endLineWithDot(problem.definition.id.displayName))
                }
            }
        }
    }

    @Override
    TestFile source(String path) {
        return file(path)
    }

    protected createMyTransformAction() {
        file("src/main/java/MyTransformAction.java") << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.file.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;
            import org.gradle.work.*;
            import java.io.*;

            @DisableCachingByDefault(because = "test transform action")
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
    }

}

class ValidatePluginsPart1IntegrationTest extends AbstractValidatePluginsIntegrationTest {

    def "supports recursive types"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
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
        assertValidationFailsWith([error(missingAnnotationConfig { type('MyTask').property('tree.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation')])

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == "Type 'MyTask' property 'tree.nonAnnotated' property missing"
            details == "A property without annotation isn't considered during up-to-date checking"
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal'
            ]
            additionalData == [
                'parentPropertyName' : 'tree',
                'typeName' : 'MyTask',
                'propertyName' : 'nonAnnotated'
            ]
        }
    }

    def "task cannot have property with annotation @#ann.simpleName"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
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
            error(annotationInvalidInContextConfig { annotation(ann.simpleName).type('MyTask').property('options.nestedThing').forTask() }, 'validation_problems', 'annotation_invalid_in_context'),
            error(annotationInvalidInContextConfig { annotation(ann.simpleName).type('MyTask').property('thing').forTask() }, 'validation_problems', 'annotation_invalid_in_context')
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            contextualLabel == 'Type \'MyTask\' property \'options.nestedThing\' is annotated with invalid property type'
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Destroys, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @LocalState, @Nested, @OptionValues, @OutputDirectories, @OutputDirectory, @OutputFile, @OutputFiles, @ReplacedBy or @ServiceReference',
            ]
            additionalData == [
                'parentPropertyName' : 'options',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedThing',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            contextualLabel == 'Type \'MyTask\' property \'thing\' is annotated with invalid property type'
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Destroys, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @LocalState, @Nested, @OptionValues, @OutputDirectories, @OutputDirectory, @OutputFile, @OutputFiles, @ReplacedBy or @ServiceReference',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'thing',
            ]
        }

        where:
        ann << [InputArtifact, InputArtifactDependencies]
    }

    def "can enable stricter validation"() {
        enableProblemsApiCheck()
        buildFile << """
            dependencies {
                implementation localGroovy()
            }
            def strictProp = providers.gradleProperty("strict")
            validatePlugins.enableStricterValidation = strictProp.present
        """

        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.work.*

            @DisableCachingByDefault(because = "test task")
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
            error(missingNormalizationStrategyConfig { type('MyTask').property('dirProp').annotatedWith('InputDirectory') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategyConfig { type('MyTask').property('fileProp').annotatedWith('InputFile') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategyConfig { type('MyTask').property('filesProp').annotatedWith('InputFiles') }, 'validation_problems', 'missing_normalization_annotation'),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            definition.id.fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'dirProp\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'dirProp',
            ]
        }
        verifyAll(receivedProblem(1)) {
            definition.id.fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileProp\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileProp',
            ]
        }
        verifyAll(receivedProblem(2)) {
            definition.id.fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'filesProp\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'filesProp',
            ]
        }
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
            import org.gradle.work.*;
            import java.io.File;
            import com.typesafe.config.Config;

            @DisableCachingByDefault(because = "test task")
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
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
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
        createMyTransformAction()

        expect:
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTransformAction').property('badTime').missingInput() }, 'validation_problems', 'missing_annotation'),
            error(annotationInvalidInContextConfig { annotation('InputFile').type('MyTransformAction').property('inputFile').forTransformAction() }, 'validation_problems', 'annotation_invalid_in_context'),
            error(missingAnnotationConfig { type('MyTransformAction').property('oldThing').missingInput() }, 'validation_problems', 'missing_annotation'),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            contextualLabel == 'Type \'MyTransformAction\' property \'inputFile\' is annotated with invalid property type'
            details == 'The \'@InputFile\' annotation cannot be used in this context'
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Inject, @InputArtifact or @InputArtifactDependencies',
            ]
            additionalData == [
                'typeName' : 'MyTransformAction',
                'propertyName' : 'inputFile',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformAction\' property \'badTime\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'typeName' : 'MyTransformAction',
                'propertyName' : 'badTime',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformAction\' property \'oldThing\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'typeName' : 'MyTransformAction',
                'propertyName' : 'oldThing',
            ]
        }
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
            error(missingAnnotationConfig { type('MyTransformParameters').property('badTime').missingInput() }, 'validation_problems', 'missing_annotation'),
            error(incompatibleAnnotationsConfig { type('MyTransformParameters').property('incrementalNonFileInput').annotatedWith('Incremental').incompatibleWith('Input') }, 'validation_problems', 'incompatible_annotations'),
            error(annotationInvalidInContextConfig { annotation('InputArtifact').type('MyTransformParameters').property('inputFile') }, 'validation_problems', 'annotation_invalid_in_context'),
            error(missingAnnotationConfig { type('MyTransformParameters').property('oldThing').missingInput() }, 'validation_problems', 'missing_annotation'),
        ])

         and:
         verifyAll(receivedProblem(0)) {
             fqid == 'validation:property-validation:annotation-invalid-in-context'
             contextualLabel == 'Type \'MyTransformParameters\' property \'inputFile\' is annotated with invalid property type'
             details == 'The \'@InputArtifact\' annotation cannot be used in this context'
             solutions == [
                 'Remove the property',
                 'Use a different annotation, e.g one of @Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested, @ReplacedBy or @ServiceReference',
             ]
             additionalData == [
                 'typeName' : 'MyTransformParameters',
                 'propertyName' : 'inputFile',
             ]
         }
         verifyAll(receivedProblem(1)) {
             fqid == 'validation:property-validation:incompatible-annotations'
             contextualLabel == 'Type \'MyTransformParameters\' property \'incrementalNonFileInput\' Wrong property annotation'
             details == 'This modifier is used in conjunction with a property of type \'Input\' but this doesn\'t have semantics'
             solutions == [ 'Remove the \'@Incremental\' annotation' ]
             additionalData == [
                 'typeName' : 'MyTransformParameters',
                 'propertyName' : 'incrementalNonFileInput',
             ]
         }
         verifyAll(receivedProblem(2)) {
             fqid == 'validation:property-validation:missing-annotation'
             contextualLabel == 'Type \'MyTransformParameters\' property \'badTime\' property missing'
             details == 'A property without annotation isn\'t considered during up-to-date checking'
             solutions == [
                 'Add an input annotation',
                 'Mark it as @Internal',
             ]
             additionalData == [
                 'typeName' : 'MyTransformParameters',
                 'propertyName' : 'badTime',
             ]
         }
         verifyAll(receivedProblem(3)) {
             fqid == 'validation:property-validation:missing-annotation'
             contextualLabel == 'Type \'MyTransformParameters\' property \'oldThing\' property missing'
             details == 'A property without annotation isn\'t considered during up-to-date checking'
             solutions == [
                 'Add an input annotation',
                 'Mark it as @Internal',
             ]
             additionalData == [
                 'typeName' : 'MyTransformParameters',
                 'propertyName' : 'oldThing',
             ]
         }
    }

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
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
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
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
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
            error(missingAnnotationConfig { type('PluginTask').property('badProperty').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'PluginTask\' property \'badProperty\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'typeName' : 'PluginTask',
                'propertyName' : 'badProperty',
            ]
        }
    }

    def "detects missing DisableCachingByDefault annotations"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public abstract class MyTask extends DefaultTask {
            }
        """
        file("src/main/java/MyTransformAction.java") << """
            import org.gradle.api.artifacts.transform.*;

            public abstract class MyTransformAction implements TransformAction<TransformParameters.None> {
            }
        """
        buildFile << """
            validatePlugins.enableStricterValidation = true
        """

        expect:
        assertValidationFailsWith([
            warning("""
                Type 'MyTask' must be annotated either with @CacheableTask or with @DisableCachingByDefault.

                Reason: The task author should make clear why a task is not cacheable.

                Possible solutions:
                  1. Add @DisableCachingByDefault(because = ...).
                  2. Add @CacheableTask.
                  3. Add @UntrackedTask(because = ...).
            """.stripIndent(true).trim(), "validation_problems", "disable_caching_by_default"),
            warning("""
                Type 'MyTransformAction' must be annotated either with @CacheableTransform or with @DisableCachingByDefault.

                Reason: The transform action author should make clear why a transform action is not cacheable.

                Possible solutions:
                  1. Add @DisableCachingByDefault(because = ...).
                  2. Add @CacheableTransform.
            """.stripIndent(true).trim(), "validation_problems", "disable_caching_by_default")
        ])

         and:
         verifyAll(receivedProblem(0)) {
             fqid == 'validation:type-validation:not-cacheable-without-reason'
             contextualLabel == 'Type \'MyTask\' annotation missing'
             details == 'The task author should make clear why a task is not cacheable'
             solutions == [
                 'Add @DisableCachingByDefault(because = ...)',
                 'Add @CacheableTask',
                 'Add @UntrackedTask(because = ...)',
             ]
             additionalData == [ 'typeName' : 'MyTask' ]
         }
         verifyAll(receivedProblem(1)) {
             fqid == 'validation:type-validation:not-cacheable-without-reason'
             contextualLabel == 'Type \'MyTransformAction\' annotation missing'
             details == 'The transform action author should make clear why a transform action is not cacheable'
             solutions == [
                 'Add @DisableCachingByDefault(because = ...)',
                 'Add @CacheableTransform',
             ]
             additionalData == [ 'typeName' : 'MyTransformAction' ]
         }
    }

    def "untracked tasks don't need a disable caching by default reason"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            @UntrackedTask(because = "untracked for validation test")
            public abstract class MyTask extends DefaultTask {
            }
        """
        buildFile << """
            validatePlugins.enableStricterValidation = true
        """

        expect:
        assertValidationSucceeds()
    }

    def "can not use ResolvedArtifactResult as task input annotated with @#annotation"() {
        given:
        source("src/main/java/NestedBean.java") << """
            import org.gradle.api.artifacts.result.ResolvedArtifactResult;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;

            public interface NestedBean {

                @$annotation
                Property<ResolvedArtifactResult> getNestedInput();
            }
        """
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.artifacts.result.ResolvedArtifactResult;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault
            public abstract class MyTask extends DefaultTask {

                private final NestedBean nested = getProject().getObjects().newInstance(NestedBean.class);

                @$annotation
                public ResolvedArtifactResult getDirect() { return null; }

                @$annotation
                public Provider<ResolvedArtifactResult> getProviderInput() { return getPropertyInput(); }

                @$annotation
                public abstract Property<ResolvedArtifactResult> getPropertyInput();

                @$annotation
                public abstract SetProperty<ResolvedArtifactResult> getSetPropertyInput();

                @$annotation
                public abstract ListProperty<ResolvedArtifactResult> getListPropertyInput();

                @$annotation
                public abstract MapProperty<String, ResolvedArtifactResult> getMapPropertyInput();

                @Nested
                public NestedBean getNestedBean() { return nested; }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=7")
        assertValidationFailsWith([
            error(unsupportedValueTypeConfig { type('MyTask').property('direct').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('ResolvedArtifactResult').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('listPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('ListProperty<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('mapPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('MapProperty<String, ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('nestedBean.nestedInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Property<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('propertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Property<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('providerInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Provider<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
            error(unsupportedValueTypeConfig { type('MyTask').property('setPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('SetProperty<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'direct\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'direct',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'listPropertyInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'listPropertyInput',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'mapPropertyInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'mapPropertyInput',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'nestedBean.nestedInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'parentPropertyName' : 'nestedBean',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedInput',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'propertyInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'propertyInput',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'providerInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'providerInput',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == 'Type \'MyTask\' property \'setPropertyInput\' property with unsupported annotation'
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'setPropertyInput',
            ]
        }

        where:
        annotation   | _
        "Input"      | _
        "InputFile"  | _
        "InputFiles" | _
    }
}

class ValidatePluginsPart2IntegrationTest extends AbstractValidatePluginsIntegrationTest {
    @Issue("https://github.com/gradle/gradle/issues/24979")
    def "cannot annotate type 'java.net.URL' with @Input"() {
        given:
        source("src/main/java/NestedBean.java") << """
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import java.net.URL;

            public interface NestedBean {

                @Input
                Property<URL> getNestedInput();
            }
        """
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.net.URL;

            @DisableCachingByDefault
            public abstract class MyTask extends DefaultTask {

                private final NestedBean nested = getProject().getObjects().newInstance(NestedBean.class);

                @Input
                public URL getDirect() { return null; }

                @Input
                public Provider<URL> getProviderInput() { return getPropertyInput(); }

                @Input
                public abstract Property<URL> getPropertyInput();

                @Input
                public abstract SetProperty<URL> getSetPropertyInput();

                @Input
                public abstract ListProperty<URL> getListPropertyInput();

                @Input
                public abstract MapProperty<String, URL> getMapPropertyInput();

                @Nested
                public NestedBean getNestedBean() { return nested; }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=7")
        assertValidationFailsWith([
            warning(unsupportedValueTypeConfig { type('MyTask').property('direct').propertyType('URL') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('listPropertyInput').propertyType('ListProperty<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('mapPropertyInput').propertyType('MapProperty<String, URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('nestedBean.nestedInput').propertyType('Property<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('propertyInput').propertyType('Property<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('providerInput').propertyType('Provider<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueTypeConfig { type('MyTask').property('setPropertyInput').propertyType('SetProperty<URL>') }, "validation_problems", "unsupported_value_type"),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'direct\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'direct',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'listPropertyInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'listPropertyInput',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'mapPropertyInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'mapPropertyInput',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'nestedBean.nestedInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'parentPropertyName' : 'nestedBean',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedInput',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'propertyInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'propertyInput',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'providerInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'providerInput',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'setPropertyInput\' has @Input annotation used'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'setPropertyInput',
            ]
        }
    }

    def "detects problems with file inputs"() {
        file("input.txt").text = "input"
        file("input").createDir()

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.file.*;
            import org.gradle.api.tasks.*;
            import java.util.Set;
            import java.util.Collections;
            import java.io.File;
            import java.nio.file.Path;

            @CacheableTask
            public abstract class MyTask extends DefaultTask {
                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver;

                @InputDirectory
                @Optional
                public File getInputDirectory() {
                    return new File("input");
                }

                @InputFile
                public File getInputFile() {
                    return new File("input.txt");
                }

                @InputFiles
                public Set<File> getInputFiles() {
                    return Collections.emptySet();
                }

                @Input
                public File getFile() {
                    return new File("some-file");
                }

                @Input
                public FileCollection getFileCollection() {
                    return getProject().files();
                }

                @Input
                public Path getFilePath() {
                    return new File("some-file").toPath();
                }

                @Input
                public FileTree getFileTree() {
                    return getProject().files().getAsFileTree();
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        when:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=7")

        then:
        assertValidationFailsWith([
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('file').propertyType('File') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('fileCollection').propertyType('FileCollection') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('filePath').propertyType('Path') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('fileTree').propertyType('FileTree') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(missingNormalizationStrategyConfig { type('MyTask').property('inputDirectory').annotatedWith('InputDirectory') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategyConfig { type('MyTask').property('inputFile').annotatedWith('InputFile') }, 'validation_problems', 'missing_normalization_annotation'),
            error(missingNormalizationStrategyConfig { type('MyTask').property('inputFiles').annotatedWith('InputFiles') }, 'validation_problems', 'missing_normalization_annotation'),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'file\' has @Input annotation used on property'
            details == 'A property of type \'File\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'file',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileCollection\' has @Input annotation used on property'
            details == 'A property of type \'FileCollection\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileCollection',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'filePath\' has @Input annotation used on property'
            details == 'A property of type \'Path\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'filePath',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileTree\' has @Input annotation used on property'
            details == 'A property of type \'FileTree\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileTree',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputDirectory\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'inputDirectory',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputFile\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'inputFile',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputFiles\' Missing normalization'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'inputFiles',
            ]
        }
    }

    def "detects problems on nested collections"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;
            import java.io.File;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @Nested
                public List<Options> getOptionsList() {
                    return Arrays.asList(new Options());
                }

                @Nested
                public Iterable<Options> getIterableOptions() {
                    return Arrays.asList(new Options());
                }

                @Nested
                public Iterable<Iterable<Options>> getDoubleIterableOptions() {
                    return Arrays.asList(Arrays.asList(new Options()));
                }

                @Nested
                public Map<String, Options> getMappedOptions() {
                    return Collections.singletonMap("alma", new Options());
                }

                @Nested
                public Iterable<Map<String, Iterable<Options>>> getIterableMappedOptions() {
                    return Arrays.asList(Collections.singletonMap("alma", Arrays.asList(new Options())));
                }

                @Nested
                public Provider<Options> getProvidedOptions() {
                    return getProject().getObjects().property(Options.class).convention(new Options());
                }

                @Nested
                public Iterable<NamedBean> getNamedIterable() {
                    return Arrays.asList(new NamedBean());
                }

                @Nested
                public AnnotatedList getAnnotatedList() {
                    return new AnnotatedList();
                }

                public static class Options {
                    @Input
                    public String getGood() {
                        return "good";
                    }

                    public String getNotAnnotated() {
                        return null;
                    }
                }

                public static class NamedBean implements Named {
                    @Input
                    public String getGood() {
                        return "good";
                    }

                    public String getNotAnnotated() {
                        return null;
                    }

                    @Internal
                    public String getName() {
                        return "tibor";
                    }
                }

                // Does not validate the type parameter of extended collection
                // because it has annotated properties
                public static class AnnotatedList extends ArrayList<Options> {
                    public AnnotatedList() {
                        add(new Options());
                    }

                    @Input
                    public String getGood() {
                        return "good";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=8")
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTask').property("doubleIterableOptions${iterableSymbol}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("iterableMappedOptions${iterableSymbol}${getKeySymbolFor("alma")}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("iterableOptions${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("mappedOptions${getKeySymbolFor("alma")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("namedIterable${getNameSymbolFor("tibor")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("options.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("optionsList${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property("providedOptions.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'doubleIterableOptions.*.*.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'doubleIterableOptions.*.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'iterableMappedOptions.*.<key>.*.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'iterableMappedOptions.*.<key>.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'iterableOptions.*.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'iterableOptions.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'mappedOptions.<key>.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'mappedOptions.<key>',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'namedIterable.<name>.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'namedIterable.<name>',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'options.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'options',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'optionsList.*.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'optionsList.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(7)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'providedOptions.notAnnotated\' property missing'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData == [
                'parentPropertyName' : 'providedOptions',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/23045")
    def "nested map with #supportedType key is validated without warning"() {
        def gStringValue = "foo"
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @Nested
                public Map<String, Options> getMapWithGStringKey() {
                    return Collections.singletonMap("$gStringValue", new Options());
                }

                @Nested
                public Map<$supportedType, Options> getMapWithSupportedKey() {
                    return Collections.singletonMap($value, new Options());
                }

                @Nested
                public Map<$supportedType, Options> getMapEmpty() {
                    return Collections.emptyMap();
                }

                public static class Options {
                    @Input
                    public String getGood() {
                        return "good";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }

            enum Letter { A, B, C }
        """

        expect:
        assertValidationSucceeds()

        where:
        supportedType | value
        'Integer'     | 'Integer.valueOf(0)'
        'String'      | '"foo"'
        'Enum'        | 'Letter.A'
    }

    @Issue("https://github.com/gradle/gradle/issues/23045")
    def "nested map with unsupported key type is validated with warning"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @Nested
                public Map<Boolean, Options> getMapWithUnsupportedKey() {
                    return Collections.singletonMap(true, new Options());
                }

                public static class Options {
                    @Input
                    public String getGood() {
                        return "good";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=1")
        assertValidationFailsWith([
            warning(nestedMapUnsupportedKeyTypeConfig { type('MyTask').property("mapWithUnsupportedKey").keyType("java.lang.Boolean") }, 'validation_problems', 'unsupported_key_type_of_nested_map'),
        ])

        and:
        verifyAll(receivedProblem) {
            fqid == 'validation:property-validation:nested-map-unsupported-key-type'
            contextualLabel == 'Type \'MyTask\' property \'mapWithUnsupportedKey\' where key of nested map'
            details == 'Key of nested map must be one of the following types: \'Enum\', \'Integer\', \'String\''
            solutions == [ 'Change type of key to one of the following types: \'Enum\', \'Integer\', \'String\'' ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : 'mapWithUnsupportedKey',
            ]
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/23049")
    def "nested #typeName#parameterType is validated with warning"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public $typeName$parameterType getMy$typeName() {
                    return $producer;
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        def reason = "Type is in 'java.*' or 'javax.*' package that are reserved for standard Java API types."
        assertValidationFailsWith([
            warning(nestedTypeUnsupportedConfig { type("MyTask").property("my$typeName").annotatedType(className).reason(reason) },
                'validation_problems', 'unsupported_nested_type'),
        ])

        and:
        verifyAll(receivedProblem) {
            fqid == 'validation:property-validation:nested-type-unsupported'
            contextualLabel == "Type 'MyTask' property 'my$typeName' with nested type"
            details == 'Type is in \'java.*\' or \'javax.*\' package that are reserved for standard Java API types'
            solutions == [
                'Use a different input annotation if type is not a bean',
                'Use a different package that doesn\'t conflict with standard Java or Kotlin types for custom types',
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : "my$typeName",
            ]
        }

        where:
        typeName   | parameterType      | producer                                                            | className
        'Integer'  | ''                 | 'Integer.valueOf(1)'                                                | 'java.lang.Integer'
        'String'   | ''                 | 'new String()'                                                      | 'java.lang.String'
        'Iterable' | '<Integer>'        | 'Arrays.asList(Integer.valueOf(1), Integer.valueOf(2))'             | 'java.lang.Integer'
        'List'     | '<String>'         | 'Arrays.asList("value1", "value2")'                                 | 'java.lang.String'
        'Map'      | '<String,Integer>' | 'Collections.singletonMap("a", Integer.valueOf(1))'                 | 'java.lang.Integer'
        'Provider' | '<Boolean>'        | 'getProject().getProviders().provider(() -> Boolean.valueOf(true))' | 'java.lang.Boolean'
    }

    @Issue("https://github.com/gradle/gradle/issues/23049")
    def "nested #typeName#parameterType is validated without warning"() {
        groovyTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.io.*;
            import java.util.*;

            enum SomeEnum { A, B, C }

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @Nested
                public $typeName$parameterType getMy$typeName() {
                    return $producer;
                }

                public static class Options {
                    @Input
                    public String getGood() {
                        return "good";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        assertValidationSucceeds()

        where:
        typeName   | parameterType      | producer
        'Options'  | ''                 | 'new Options()'
        'SomeEnum' | ''                 | 'SomeEnum.A'
        'File'     | ''                 | 'new File("some/path")'  // Not invalidated because type is not final
        'GString'  | ''                 | 'GString.EMPTY'          // Not invalidated because type is not final
        'Iterable' | '<Options>'        | 'Arrays.asList(new Options(), new Options())'
        'Map'      | '<String,Options>' | 'Collections.singletonMap("a", new Options())'
        'Provider' | '<Options>'        | 'getProject().getProviders().provider(() -> new Options())'
    }

    @Issue("https://github.com/gradle/gradle/issues/23049")
    def "nested Kotlin #typeName is validated with warning"() {
        kotlinTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import kotlin.*;

            abstract class MyTask : DefaultTask() {
                @get:Nested
                var my$typeName: $typeName = $producer

                @TaskAction
                fun execute() { }
            }
        """

        expect:
        assertValidationFailsWith([
            warning(nestedTypeUnsupportedConfig { type("MyTask").property("my$typeName").annotatedType(className).reason(reason) },
                'validation_problems', 'unsupported_nested_type'),
        ])

        and:
        verifyAll(receivedProblem) {
            fqid == 'validation:property-validation:nested-type-unsupported'
            contextualLabel == "Type 'MyTask' property 'my$typeName' with nested type"
            reason == "$details."
            solutions == [
                'Use a different input annotation if type is not a bean',
                'Use a different package that doesn\'t conflict with standard Java or Kotlin types for custom types'
            ]
            additionalData == [
                'typeName' : 'MyTask',
                'propertyName' : "my$typeName"
            ]
        }

        where:
        typeName           | producer                   | className                 | reason
        'DeprecationLevel' | 'DeprecationLevel.WARNING' | 'kotlin.DeprecationLevel' | "Type is in 'kotlin.*' package that is reserved for Kotlin stdlib types."
        'String'           | '"abc"'                    | 'java.lang.String'        | "Type is in 'java.*' or 'javax.*' package that are reserved for standard Java API types."
    }

    def "honors configured Java Toolchain to avoid compiled by a more recent version failure"() {
        disableProblemsApiCheck()
        def currentJdk = Jvm.current()
        def newerJdk = AvailableJavaHomes.getDifferentVersion { it.languageVersion > currentJdk.javaVersion }
        assumeNotNull(newerJdk)

        def installationPaths = [currentJdk, newerJdk].collect { it.javaHome.absolutePath }.join(",")

        given:
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public abstract class MyTask extends DefaultTask {
            }
        """
        executer.withArgument("-Porg.gradle.java.installations.paths=" + installationPaths)
        buildFile << """
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(${newerJdk.javaVersion.majorVersion}))
                }
            }
        """
        expect:
        assertValidationSucceeds()
    }

    def "missing Java Toolchain plugin causes a deprecation warning"() {
        given:
        source("producer/settings.gradle") << ""
        source("producer/build.gradle") << "plugins { id 'java' }"
        source("producer/src/main/java/Test.java") << "public class Test {}"

        source("consumer/settings.gradle") << ""
        source("consumer/build.gradle") << """
            tasks.register("validatePlugins", ValidatePlugins) {
                classes.from("../producer/build/classes/java/main")
                outputFile.set(project.file("\$buildDir/report.txt"))
            }
        """

        when:
        executer.inDirectory(file("producer"))
            .withArgument("build")
            .run()

        executer.inDirectory(file("consumer"))
            .expectDocumentedDeprecationWarning(
                "Using task ValidatePlugins without applying the Java Toolchain plugin. " +
                    "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#validate_plugins_without_java_toolchain"
            )

        then:
        succeeds "validatePlugins"

        and:
        verifyAll(receivedProblem) {
            fqid == 'deprecation:using-task-validateplugins-without-applying-the-java-toolchain-plugin'
            contextualLabel == 'Using task ValidatePlugins without applying the Java Toolchain plugin. This behavior has been deprecated.'
        }

    }
}
