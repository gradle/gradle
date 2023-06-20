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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.getPluralEnding
import static org.hamcrest.Matchers.containsString
import static org.junit.Assume.assumeNotNull

class ValidatePluginsIntegrationTest extends AbstractPluginValidationIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java-gradle-plugin"
        """
    }

    String iterableSymbol = '.*'

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
                fullMessage = "${fullMessage}\n${learnAt(it.id, it.section)}"
            }
            [(fullMessage): it.severity]
        })

        failure.assertHasCause "Plugin validation failed with ${messages.size()} problem${getPluralEnding(messages)}"
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
        assertValidationFailsWith([
            error(missingAnnotationMessage { type('MyTask').property('tree.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor(
        ValidationProblemId.ANNOTATION_INVALID_IN_CONTEXT
    )
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
            error(missingAnnotationMessage { type('PluginTask').property('badProperty').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @ValidationTestFor(ValidationProblemId.NOT_CACHEABLE_WITHOUT_REASON)
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
    }

    @ValidationTestFor(ValidationProblemId.NOT_CACHEABLE_WITHOUT_REASON)
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

    @ValidationTestFor(ValidationProblemId.UNSUPPORTED_VALUE_TYPE)
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
                error(unsupportedValueType { type('MyTask').property('direct').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('ResolvedArtifactResult').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('listPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('ListProperty<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('mapPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('MapProperty<String, ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('nestedBean.nestedInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Property<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('propertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Property<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('providerInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('Provider<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
                error(unsupportedValueType { type('MyTask').property('setPropertyInput').annotationType(annotation).unsupportedValueType('ResolvedArtifactResult').propertyType('SetProperty<ResolvedArtifactResult>').solution('Extract artifact metadata and annotate with @Input.').solution('Extract artifact files and annotate with @InputFiles.') }, "validation_problems", "unsupported_value_type"),
        ])

        where:
        annotation   | _
        "Input"      | _
        "InputFile"  | _
        "InputFiles" | _
    }

    @Issue("https://github.com/gradle/gradle/issues/24979")
    @ValidationTestFor(ValidationProblemId.UNSUPPORTED_VALUE_TYPE)
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
            warning(unsupportedValueType { type('MyTask').property('direct').propertyType('URL') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('listPropertyInput').propertyType('ListProperty<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('mapPropertyInput').propertyType('MapProperty<String, URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('nestedBean.nestedInput').propertyType('Property<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('propertyInput').propertyType('Property<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('providerInput').propertyType('Provider<URL>') }, "validation_problems", "unsupported_value_type"),
            warning(unsupportedValueType { type('MyTask').property('setPropertyInput').propertyType('SetProperty<URL>') }, "validation_problems", "unsupported_value_type"),
        ])
    }

    @ValidationTestFor([
            ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION,
            ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION
    ])
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

        expect:
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=7")
        assertValidationFailsWith([
                error(incorrectUseOfInputAnnotation { type('MyTask').property('file').propertyType('File') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('fileCollection').propertyType('FileCollection') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('filePath').propertyType('Path') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(incorrectUseOfInputAnnotation { type('MyTask').property('fileTree').propertyType('FileTree') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
                error(missingNormalizationStrategy { type('MyTask').property('inputDirectory').annotatedWith('InputDirectory') }, 'validation_problems', 'missing_normalization_annotation'),
                error(missingNormalizationStrategy { type('MyTask').property('inputFile').annotatedWith('InputFile') }, 'validation_problems', 'missing_normalization_annotation'),
                error(missingNormalizationStrategy { type('MyTask').property('inputFiles').annotatedWith('InputFiles') }, 'validation_problems', 'missing_normalization_annotation'),
        ])
    }

    @ValidationTestFor(
            ValidationProblemId.MISSING_ANNOTATION
    )
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
                error(missingAnnotationMessage { type('MyTask').property("doubleIterableOptions${iterableSymbol}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("iterableMappedOptions${iterableSymbol}${getKeySymbolFor("alma")}${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("iterableOptions${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("mappedOptions${getKeySymbolFor("alma")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("namedIterable${getNameSymbolFor("tibor")}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("options.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("optionsList${iterableSymbol}.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
                error(missingAnnotationMessage { type('MyTask').property("providedOptions.notAnnotated").missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
    }

    @Issue("https://github.com/gradle/gradle/issues/23045")
    @ValidationTestFor(
        ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE
    )
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
    @ValidationTestFor(
        ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE
    )
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
            warning(nestedMapUnsupportedKeyType { type('MyTask').property("mapWithUnsupportedKey").keyType("java.lang.Boolean") }, 'validation_problems', 'unsupported_key_type_of_nested_map'),
        ])
    }

    @Issue("https://github.com/gradle/gradle/issues/23049")
    @ValidationTestFor(
        ValidationProblemId.NESTED_TYPE_UNSUPPORTED
    )
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
        assertValidationFailsWith([
            warning(nestedTypeUnsupported { type("MyTask").property("my$typeName").annotatedType(className) },
                'validation_problems', 'unsupported_nested_type'),
        ])

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
    @ValidationTestFor(
        ValidationProblemId.NESTED_TYPE_UNSUPPORTED
    )
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
    @ValidationTestFor(ValidationProblemId.NESTED_TYPE_UNSUPPORTED)
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
            warning(nestedTypeUnsupported { type("MyTask").property("my$typeName").annotatedType(className) },
                'validation_problems', 'unsupported_nested_type'),
        ])

        where:
        typeName            | producer                    | className
        'DeprecationLevel'  | 'DeprecationLevel.WARNING'  | 'kotlin.DeprecationLevel'
        'String'            | '"abc"'                     | 'java.lang.String'
    }

    def "honors configured Java Toolchain to avoid compiled by a more recent version failure"() {
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
    }
}
