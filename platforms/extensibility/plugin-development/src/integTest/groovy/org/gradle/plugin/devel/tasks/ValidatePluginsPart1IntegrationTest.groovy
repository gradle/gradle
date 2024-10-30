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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker

class ValidatePluginsPart1IntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker, ValidatePluginsTrait {

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
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'tree.nonAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'tree',
                'typeName' : 'MyTask',
                'propertyName' : 'nonAnnotated',
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
            contextualLabel == "Type \'MyTask\' property \'options.nestedThing\' is annotated with invalid property type @$ann.simpleName"
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Destroys, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @LocalState, @Nested, @OptionValues, @OutputDirectories, @OutputDirectory, @OutputFile, @OutputFiles, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'options',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedThing',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:annotation-invalid-in-context'
            contextualLabel == "Type 'MyTask' property 'thing' is annotated with invalid property type @$ann.simpleName"
            details == "The '@${ann.simpleName}' annotation cannot be used in this context"
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Destroys, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @LocalState, @Nested, @OptionValues, @OutputDirectories, @OutputDirectory, @OutputFile, @OutputFiles, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == [
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
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'dirProp\' is annotated with @InputDirectory but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'dirProp',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileProp\' is annotated with @InputFile but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileProp',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'filesProp\' is annotated with @InputFiles but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
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
            contextualLabel == 'Type \'MyTransformAction\' property \'inputFile\' is annotated with invalid property type @InputFile'
            details == 'The \'@InputFile\' annotation cannot be used in this context'
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Inject, @InputArtifact or @InputArtifactDependencies',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTransformAction',
                'propertyName' : 'inputFile',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformAction\' property \'badTime\' is missing an input annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTransformAction',
                'propertyName' : 'badTime',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformAction\' property \'oldThing\' is missing an input annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
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
            contextualLabel == 'Type \'MyTransformParameters\' property \'inputFile\' is annotated with invalid property type @InputArtifact'
            details == 'The \'@InputArtifact\' annotation cannot be used in this context'
            solutions == [
                'Remove the property',
                'Use a different annotation, e.g one of @Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested, @ReplacedBy or @ServiceReference',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTransformParameters',
                'propertyName' : 'inputFile',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:incompatible-annotations'
            contextualLabel == 'Type \'MyTransformParameters\' property \'incrementalNonFileInput\' is annotated with @Incremental but that is not allowed for \'Input\' properties'
            details == 'This modifier is used in conjunction with a property of type \'Input\' but this doesn\'t have semantics'
            solutions == [ 'Remove the \'@Incremental\' annotation' ]
            additionalData.asMap == [
                'typeName' : 'MyTransformParameters',
                'propertyName' : 'incrementalNonFileInput',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformParameters\' property \'badTime\' is missing an input annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTransformParameters',
                'propertyName' : 'badTime',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTransformParameters\' property \'oldThing\' is missing an input annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
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
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'PluginTask\' property \'badProperty\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
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
             contextualLabel == 'Type \'MyTask\' must be annotated either with @CacheableTask or with @DisableCachingByDefault'
             details == 'The task author should make clear why a task is not cacheable'
             solutions == [
                 'Add @DisableCachingByDefault(because = ...)',
                 'Add @CacheableTask',
                 'Add @UntrackedTask(because = ...)',
             ]
             additionalData.asMap == [ 'typeName' : 'MyTask' ]
         }
         verifyAll(receivedProblem(1)) {
             fqid == 'validation:type-validation:not-cacheable-without-reason'
             contextualLabel == 'Type \'MyTransformAction\' must be annotated either with @CacheableTransform or with @DisableCachingByDefault'
             details == 'The transform action author should make clear why a transform action is not cacheable'
             solutions == [
                 'Add @DisableCachingByDefault(because = ...)',
                 'Add @CacheableTransform',
             ]
             additionalData.asMap == [ 'typeName' : 'MyTransformAction' ]
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
            contextualLabel == "Type 'MyTask' property 'direct' has @$annotation annotation used on property of type 'ResolvedArtifactResult'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'direct',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'listPropertyInput' has @$annotation annotation used on property of type 'ListProperty<ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'listPropertyInput',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'mapPropertyInput' has @$annotation annotation used on property of type 'MapProperty<String, ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'mapPropertyInput',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'nestedBean.nestedInput' has @$annotation annotation used on property of type 'Property<ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'nestedBean',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedInput',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'propertyInput' has @$annotation annotation used on property of type 'Property<ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'propertyInput',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'providerInput' has @$annotation annotation used on property of type 'Provider<ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'providerInput',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:unsupported-value-type'
            contextualLabel == "Type 'MyTask' property 'setPropertyInput' has @$annotation annotation used on property of type 'SetProperty<ResolvedArtifactResult>'"
            details == "ResolvedArtifactResult is not supported on task properties annotated with @$annotation"
            solutions == [
                'Extract artifact metadata and annotate with @Input',
                'Extract artifact files and annotate with @InputFiles',
            ]
            additionalData.asMap == [
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

