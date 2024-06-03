/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.junit.Assume
import spock.lang.Issue

class ValidatePluginsPart2IntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker, ValidatePluginsTrait {
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
            contextualLabel == 'Type \'MyTask\' property \'direct\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'direct',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'listPropertyInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'listPropertyInput',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'mapPropertyInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'mapPropertyInput',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'nestedBean.nestedInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'parentPropertyName' : 'nestedBean',
                'typeName' : 'MyTask',
                'propertyName' : 'nestedInput',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'propertyInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'propertyInput',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'providerInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'providerInput',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:unsupported-value-type-for-input'
            contextualLabel == 'Type \'MyTask\' property \'setPropertyInput\' has @Input annotation used on type \'java.net.URL\' or a property of this type'
            details == 'Type \'java.net.URL\' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type'
            solutions == [ 'Use type \'java.net.URI\' instead' ]
            additionalData.asMap == [
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
            contextualLabel == 'Type \'MyTask\' property \'file\' has @Input annotation used on property of type \'File\''
            details == 'A property of type \'File\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'file',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileCollection\' has @Input annotation used on property of type \'FileCollection\''
            details == 'A property of type \'FileCollection\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileCollection',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'filePath\' has @Input annotation used on property of type \'Path\''
            details == 'A property of type \'Path\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'filePath',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:incorrect-use-of-input-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileTree\' has @Input annotation used on property of type \'FileTree\''
            details == 'A property of type \'FileTree\' annotated with @Input cannot determine how to interpret the file'
            solutions == [
                'Annotate with @InputFile for regular files',
                'Annotate with @InputFiles for collections of files',
                'If you want to track the path, return File.absolutePath as a String and keep @Input',
            ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'fileTree',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputDirectory\' is annotated with @InputDirectory but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'inputDirectory',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputFile\' is annotated with @InputFile but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
                'typeName' : 'MyTask',
                'propertyName' : 'inputFile',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'inputFiles\' is annotated with @InputFiles but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == [ 'Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath' ]
            additionalData.asMap == [
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
            contextualLabel == 'Type \'MyTask\' property \'doubleIterableOptions.*.*.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'doubleIterableOptions.*.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(1)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'iterableMappedOptions.*.<key>.*.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'iterableMappedOptions.*.<key>.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(2)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'iterableOptions.*.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'iterableOptions.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(3)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'mappedOptions.<key>.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'mappedOptions.<key>',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(4)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'namedIterable.<name>.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'namedIterable.<name>',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(5)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'options.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'options',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(6)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'optionsList.*.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'parentPropertyName' : 'optionsList.*',
                'typeName' : 'MyTask',
                'propertyName' : 'notAnnotated',
            ]
        }
        verifyAll(receivedProblem(7)) {
            fqid == 'validation:property-validation:missing-annotation'
            contextualLabel == 'Type \'MyTask\' property \'providedOptions.notAnnotated\' is missing an input or output annotation'
            details == 'A property without annotation isn\'t considered during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
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
            contextualLabel == "Type 'MyTask' property 'mapWithUnsupportedKey' where key of nested map is of type 'java.lang.Boolean'"
            details == 'Key of nested map must be one of the following types: \'Enum\', \'Integer\', \'String\''
            solutions == [ 'Change type of key to one of the following types: \'Enum\', \'Integer\', \'String\'' ]
            additionalData.asMap == [
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
            contextualLabel == "Type 'MyTask' property 'my$typeName' with nested type '$className' is not supported"
            details == 'Type is in \'java.*\' or \'javax.*\' package that are reserved for standard Java API types'
            solutions == [
                'Use a different input annotation if type is not a bean',
                'Use a different package that doesn\'t conflict with standard Java or Kotlin types for custom types',
            ]
            additionalData.asMap == [
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
            contextualLabel == "Type 'MyTask' property 'my$typeName' with nested type '$className' is not supported"
            reason == "$details."
            solutions == [
                'Use a different input annotation if type is not a bean',
                'Use a different package that doesn\'t conflict with standard Java or Kotlin types for custom types'
            ]
            additionalData.asMap == [
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
        Assume.assumeNotNull(newerJdk)

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
            fqid == 'deprecation:missing-java-toolchain-plugin'
            contextualLabel == 'Using task ValidatePlugins without applying the Java Toolchain plugin. This behavior has been deprecated.'
        }

    }
}
