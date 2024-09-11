/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.model.ReplacedBy
import org.gradle.api.problems.Severity
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.options.OptionValues
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationMessageDisplayConfiguration
import org.gradle.test.fixtures.file.TestFile

import javax.inject.Inject

abstract class AbstractPluginValidationIntegrationSpec extends AbstractIntegrationSpec implements ValidationMessageChecker {

    def "detects missing annotations on Java properties"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                // Should be ignored because it's not a getter
                public void getVoid() {
                }

                // Should be ignored because it's not a getter
                public int getWithParameter(int count) {
                    return count;
                }

                public long getter() {
                    return 0L;
                }

                // Ignored because static
                public static int getStatic() {
                    return 0;
                }

                // Ignored because injected
                @javax.inject.Inject
                public org.gradle.api.internal.file.FileResolver getInjected() {
                    throw new UnsupportedOperationException();
                }

                // Valid because it is annotated
                @Input
                public long getGoodTime() {
                    return 0;
                }

                // Valid because it is annotated
                @Nested
                public Options getOptions() {
                    return new Options();
                }

                // Valid because it is annotated
                @CompileClasspath
                public java.util.List<java.io.File> getClasspath() {
                    return new java.util.ArrayList<>();
                }

                // Invalid because it has no annotation
                public long getBadTime() {
                    return System.currentTimeMillis();
                }

                // Invalid because it has some other annotation
                @Deprecated
                public String getOldThing() {
                    return null;
                }

                public static class Options {
                    // Valid because it is annotated
                    @Input
                    public int getGoodNested() {
                        return 1;
                    }

                    // Invalid because there is no annotation
                    public int getBadNested() {
                        return -1;
                    }
                }

                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTask').property('badTime').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('oldThing').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('options.badNested').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('ter').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'badTime\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'badTime',
                ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'oldThing\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'oldThing',
                ]
            }
            verifyAll(receivedProblem(2)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'options.badNested\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'parentPropertyName' : 'options',
                    'typeName' : 'MyTask',
                    'propertyName' : 'badNested',
                ]
            }
            verifyAll(receivedProblem(3)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'ter\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'ter',
                ]
            }

        }
    }

    def "task can have property with annotation @#annotation.simpleName"() {
        file("input.txt").text = "input"
        file("input").createDir()

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            import java.io.File;
            import java.util.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                ${application}
                public ${type.name} getThing() {
                    return ${value};
                }

                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationSucceeds()

        where:
        annotation        | application                                                | type           | value
        Inject            | "@$Inject.name"                                            | ObjectFactory  | null
        OptionValues      | "@$OptionValues.name(\"a\")"                               | List           | null
        Internal          | '@Internal'                                                | String         | null
        ReplacedBy        | '@ReplacedBy("")'                                          | String         | null
        Console           | '@Console'                                                 | Boolean        | null
        Destroys          | '@Destroys'                                                | FileCollection | null
        LocalState        | '@LocalState'                                              | FileCollection | null
        InputFile         | '@InputFile @PathSensitive(PathSensitivity.NONE)'          | File           | "new File(\"input.txt\")"
        InputFiles        | '@InputFiles @PathSensitive(PathSensitivity.NAME_ONLY)'    | Set            | "new HashSet()"
        InputDirectory    | '@InputDirectory @PathSensitive(PathSensitivity.RELATIVE)' | File           | "new File(\"input\")"
        Input             | '@Input'                                                   | String         | "\"value\""
        OutputFile        | '@OutputFile'                                              | File           | "new File(\"output.txt\")"
        OutputFiles       | '@OutputFiles'                                             | Map            | "new HashMap<String, File>()"
        OutputDirectory   | '@OutputDirectory'                                         | File           | "new File(\"output\")"
        OutputDirectories | '@OutputDirectories'                                       | Map            | "new HashMap<String, File>()"
        Nested            | '@Nested'                                                  | List           | "new ArrayList()"
    }

    def "detects optional primitive type #primitiveType"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Optional @Input
                ${primitiveType.name} getPrimitive() {
                    return ${value};
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(optionalOnPrimitiveConfig {
                type('MyTask').property('primitive')
                    .primitive(primitiveType)
            }, "validation_problems", "cannot_use_optional_on_primitive_types"),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem) {
                fqid == 'validation:property-validation:cannot-use-optional-on-primitive-types'
                contextualLabel == "Type 'MyTask' property 'primitive' of type $primitiveType shouldn't be annotated with @Optional"
                details == 'Properties of primitive type cannot be optional'
                solutions == [
                    'Remove the @Optional annotation',
                    "Use the java.lang.$className type instead"
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'primitive'
                ]
            }
        }

        where:
        primitiveType | value       | className
        boolean       | "true"      | 'Boolean'
        int           | "1"         | 'Integer'
        double        | "1"         | 'Double'
        float         | "2f"        | 'Float'
        double        | "2d"        | 'Double'
        char          | "'c'"       | 'Character'
        short         | "(short) 5" | 'Short'
    }

    def "validates task caching annotations"() {
        javaTaskSource << """
            import org.gradle.work.*;
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;

            @CacheableTransform
            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Nested
                Options getOptions() {
                    return new Options();
                }

                @CacheableTask
                @CacheableTransform
                @DisableCachingByDefault
                public static class Options {
                    @Input
                    String getNestedThing() {
                        return "value";
                    }
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(invalidUseOfCacheableAnnotationConfig { type('MyTask').invalidAnnotation('CacheableTransform').onlyMakesSenseOn('TransformAction') }, "validation_problems", "invalid_use_of_cacheable_annotation"),
            error(invalidUseOfCacheableAnnotationConfig { type('MyTask.Options').invalidAnnotation('CacheableTask').onlyMakesSenseOn('Task') }, "validation_problems", "invalid_use_of_cacheable_annotation"),
            error(invalidUseOfCacheableAnnotationConfig { type('MyTask.Options').invalidAnnotation('CacheableTransform').onlyMakesSenseOn('TransformAction') }, "validation_problems", "invalid_use_of_cacheable_annotation"),
            error(invalidUseOfCacheableAnnotationConfig { type('MyTask.Options').invalidAnnotation('DisableCachingByDefault').onlyMakesSenseOn('Task', 'TransformAction') }, "validation_problems", "invalid_use_of_cacheable_annotation"),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:type-validation:invalid-use-of-type-annotation'
                contextualLabel == 'Type \'MyTask\' is incorrectly annotated with @CacheableTransform'
                details == 'This annotation only makes sense on TransformAction types'
                solutions == [ 'Remove the annotation' ]
                additionalData.asMap == [ 'typeName' : 'MyTask' ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:type-validation:invalid-use-of-type-annotation'
                contextualLabel == 'Type \'MyTask.Options\' is incorrectly annotated with @CacheableTask'
                details == 'This annotation only makes sense on Task types'
                solutions == [ 'Remove the annotation' ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
            verifyAll(receivedProblem(2)) {
                fqid == 'validation:type-validation:invalid-use-of-type-annotation'
                contextualLabel == 'Type \'MyTask.Options\' is incorrectly annotated with @CacheableTransform'
                details == 'This annotation only makes sense on TransformAction types'
                solutions == [ 'Remove the annotation' ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
            verifyAll(receivedProblem(3)) {
                fqid == 'validation:type-validation:invalid-use-of-type-annotation'
                contextualLabel == 'Type \'MyTask.Options\' is incorrectly annotated with @DisableCachingByDefault'
                details == 'This annotation only makes sense on Task, TransformAction types'
                solutions == [ 'Remove the annotation' ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
        }
    }

    def "detects missing annotation on Groovy properties"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.work.*

            @DisableCachingByDefault(because = "test task")
            class MyTask extends DefaultTask {
                @Input
                long goodTime

                @Nested Options options = new Options()

                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver

                long badTime

                static class Options {
                    @Input String goodNested = "good nested"
                    String badNested
                }

                @TaskAction public void execute() {}
            }
        """

        buildFile << """
            dependencies {
                implementation localGroovy()
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTask').property('badTime').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('options.badNested').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'badTime\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'badTime',
                ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'options.badNested\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'parentPropertyName' : 'options',
                    'typeName' : 'MyTask',
                    'propertyName' : 'badNested',
                ]
            }
        }
    }

    def "no problems with Copy task"() {
        file("input.txt").text = "input"

        javaTaskSource << """
            @org.gradle.work.DisableCachingByDefault(because = "my task")
            public class MyTask extends org.gradle.api.tasks.Copy {
                public MyTask() {
                    from("input.txt");
                    setDestinationDir(new java.io.File("output"));
                }
            }
        """

        expect:
        assertValidationSucceeds()
    }

    def "does not report missing properties for Provider types"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.provider.Property;
            import org.gradle.work.*;

            import java.io.File;
            import java.util.concurrent.Callable;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                private final Provider<String> text;
                private final Property<File> file;
                private final Property<Pojo> pojo;

                public MyTask() {
                    text = getProject().provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "Hello World!";
                        }
                    });
                    file = getProject().getObjects().property(File.class);
                    file.set(new File("some/dir"));
                    pojo = getProject().getObjects().property(Pojo.class);
                    pojo.set(new Pojo(true));
                }

                @Input
                public String getText() {
                    return text.get();
                }

                @OutputFile
                public File getFile() {
                    return file.get();
                }

                @Nested
                public Pojo getPojo() {
                    return pojo.get();
                }

                @TaskAction public void execute() {}
            }
        """

        source("src/main/java/Pojo.java") << """
            import org.gradle.api.tasks.Input;

            public class Pojo {
                private final boolean enabled;

                public Pojo(boolean enabled) {
                    this.enabled = enabled;
                }

                @Input
                public boolean isEnabled() {
                    return enabled;
                }
            }
        """

        expect:
        assertValidationSucceeds()
    }


    def "reports setters for property of mutable type #testedType"() {
        file("input.txt").text = "input"

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                private final ${testedType} mutableProperty = ${init};

                // getter and setter
                @InputFiles @PathSensitive(PathSensitivity.NONE)
                public ${testedType} getMutablePropertyWithSetter() { return mutableProperty; }
                public void setMutablePropertyWithSetter(${testedType} value) {}

                // just getter
                @InputFiles @PathSensitive(PathSensitivity.NONE)
                public ${testedType} getMutablePropertyWithoutSetter() { return mutableProperty; }

                // just setter
                // TODO implement warning for this case: https://github.com/gradle/gradle/issues/9341
                public void setMutablePropertyWithoutGetter() {}

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(mutableSetterConfig {
                type('MyTask').property('mutablePropertyWithSetter')
                propertyType(testedType.replaceAll("<.+>", ""))
            }, 'validation_problems', 'mutable_type_with_setter')
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem) {
                fqid == 'validation:property-validation:mutable-type-with-setter'
                contextualLabel == "Type \'MyTask\' property \'mutablePropertyWithSetter\' of mutable type '${testedType.replace('<String>', '')}' is writable"
                details == "Properties of type '${testedType.replace('<String>', '')}' are already mutable"
                solutions == [ 'Remove the \'setMutablePropertyWithSetter\' method' ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'mutablePropertyWithSetter',
                ]
            }
        }

        where:
        testedType                      | init
        ConfigurableFileCollection.name | "getProject().getObjects().fileCollection()"
        "${Property.name}<String>"      | "getProject().getObjects().property(String.class).convention(\"value\")"
        RegularFileProperty.name        | "getProject().getObjects().fileProperty().fileValue(new java.io.File(\"input.txt\"))"
    }


    def "detects annotations on private getter methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.io.File;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Input
                private long getBadTime() {
                    return 0;
                }

                @Nested
                public Options getOptions() {
                    return new Options();
                }

                public static class Options {
                    @Input
                    private String getBadNested() {
                        return "good";
                    }
                }

                @OutputDirectory
                private File getOutputDir() {
                    return new File("outputDir");
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        assertValidationFailsWith([
            error(privateGetterAnnotatedConfig { type('MyTask').property('badTime').annotation('Input') }, 'validation_problems', 'private_getter_must_not_be_annotated'),
            error(privateGetterAnnotatedConfig { type('MyTask').property('options.badNested').annotation('Input') }, 'validation_problems', 'private_getter_must_not_be_annotated'),
            error(privateGetterAnnotatedConfig { type('MyTask').property('outputDir').annotation('OutputDirectory') }, 'validation_problems', 'private_getter_must_not_be_annotated'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:private-getter-must-not-be-annotated'
                contextualLabel == 'Type \'MyTask\' property \'badTime\' is private and annotated with @Input'
                details == 'Annotations on private getters are ignored'
                solutions == [
                    'Make the getter public',
                    'Annotate the public version of the getter',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'badTime',
                ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:property-validation:private-getter-must-not-be-annotated'
                contextualLabel == 'Type \'MyTask\' property \'options.badNested\' is private and annotated with @Input'
                details == 'Annotations on private getters are ignored'
                solutions == [
                    'Make the getter public',
                    'Annotate the public version of the getter',
                ]
                additionalData.asMap == [
                    'parentPropertyName' : 'options',
                    'typeName' : 'MyTask',
                    'propertyName' : 'badNested',
                ]
            }
            verifyAll(receivedProblem(2)) {
                fqid == 'validation:property-validation:private-getter-must-not-be-annotated'
                contextualLabel == 'Type \'MyTask\' property \'outputDir\' is private and annotated with @OutputDirectory'
                details == 'Annotations on private getters are ignored'
                solutions == [
                    'Make the getter public',
                    'Annotate the public version of the getter',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'outputDir',
                ]
            }
        }
    }


    def "detects annotations on non-property methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.io.File;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Input
                public String notAGetter() {
                    return "not-a-getter";
                }

                @Nested
                public Options getOptions() {
                    return new Options();
                }

                public static class Options {
                    @Input
                    public String notANestedGetter() {
                        return "not-a-nested-getter";
                    }
                    @Input
                    public String getOther() {
                        return "valid-nested-getter";
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """
        expect:
        assertValidationFailsWith([
            error(methodShouldNotBeAnnotatedConfig { type('MyTask').kind('method').method('notAGetter').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
            error(methodShouldNotBeAnnotatedConfig { type('MyTask.Options').kind('method').method('notANestedGetter').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask\' method \'notAGetter()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask' ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask.Options\' method \'notANestedGetter()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
        }
    }


    def "detects annotations on setter methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.io.File;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Input
                public void setWriteOnly(String value) {
                }

                public String getReadWrite() {
                    return "read-write property";
                }

                @Input
                public void setReadWrite(String value) {
                }

                @Nested
                public Options getOptions() {
                    return new Options();
                }

                public static class Options {
                    @Input
                    public void setWriteOnly(String value) {
                    }

                    @Input
                    public String getReadWrite() {
                        return "read-write property";
                    }

                    @Input
                    public void setReadWrite(String value) {
                    }
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTask').property('readWrite').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(methodShouldNotBeAnnotatedConfig { type('MyTask').kind('setter').method('setReadWrite').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
            error(methodShouldNotBeAnnotatedConfig { type('MyTask').kind('setter').method('setWriteOnly').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
            error(methodShouldNotBeAnnotatedConfig { type('MyTask.Options').kind('setter').method('setReadWrite').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
            error(methodShouldNotBeAnnotatedConfig { type('MyTask.Options').kind('setter').method('setWriteOnly').annotation('Input') }, 'validation_problems', 'ignored_annotations_on_method'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:missing-annotation'
                contextualLabel == 'Type \'MyTask\' property \'readWrite\' is missing an input or output annotation'
                details == 'A property without annotation isn\'t considered during up-to-date checking'
                solutions == [
                    'Add an input or output annotation',
                    'Mark it as @Internal',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'readWrite',
                ]
            }
            verifyAll(receivedProblem(1)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask\' setter \'setReadWrite()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask' ]
            }
            verifyAll(receivedProblem(2)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask\' setter \'setWriteOnly()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask' ]
            }
            verifyAll(receivedProblem(3)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask.Options\' setter \'setReadWrite()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
            verifyAll(receivedProblem(4)) {
                fqid == 'validation:type-validation:ignored-annotations-on-method'
                contextualLabel == 'Type \'MyTask.Options\' setter \'setWriteOnly()\' should not be annotated with: @Input'
                details == 'Input/Output annotations are ignored if they are placed on something else than a getter'
                solutions == [
                    'Remove the annotations',
                    'Rename the method',
                ]
                additionalData.asMap == [ 'typeName' : 'MyTask.Options' ]
            }
        }
    }


    def "reports conflicting types when property is replaced"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                private final Property<String> newProperty = getProject().getObjects().property(String.class).convention("value");

                @Input
                @ReplacedBy("newProperty")
                public String getOldProperty() {
                    return newProperty.get();
                }

                public void setOldProperty(String oldProperty) {
                    newProperty.set(oldProperty);
                }

                @Input
                public Property<String> getNewProperty() {
                    return newProperty;
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(ignoredAnnotatedPropertyConfig { type('MyTask').property('oldProperty').ignoring('ReplacedBy').alsoAnnotatedWith('Input') }, 'validation_problems', 'ignored_property_must_not_be_annotated')
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:ignored-property-must-not-be-annotated'
                contextualLabel == 'Type \'MyTask\' property \'oldProperty\' annotated with @ReplacedBy should not be also annotated with @Input'
                details == 'A property is ignored but also has input annotations'
                solutions == [
                    'Remove the input annotations',
                    'Remove the @ReplacedBy annotation',
                ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'oldProperty',
                ]
            }
        }
    }


    def "reports both input and output annotation applied to the same property"() {
        javaTaskSource << """
            import java.io.File;
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                @Optional
                @InputFile
                @OutputFile
                public File getFile() {
                    return null;
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(conflictingAnnotationsConfig { type('MyTask').property('file').inConflict('InputFile', 'OutputFile') }, 'validation_problems', 'conflicting_annotations'),
        ])

        and:
        if (isProblemsApiCheckEnabled()) {
            verifyAll(receivedProblem(0)) {
                fqid == 'validation:property-validation:conflicting-annotations'
                contextualLabel == 'Type \'MyTask\' property \'file\' has conflicting type annotations declared: @InputFile, @OutputFile'
                details == 'The different annotations have different semantics and Gradle cannot determine which one to pick'
                solutions == [ 'Choose between one of the conflicting annotations' ]
                additionalData.asMap == [
                    'typeName' : 'MyTask',
                    'propertyName' : 'file',
                ]
            }

        }
    }

    abstract String getIterableSymbol()

    abstract String getNameSymbolFor(String name)

    abstract String getKeySymbolFor(String name)

    abstract void assertValidationSucceeds()


    abstract void assertValidationFailsWith(List<DocumentedProblem> messages)

    abstract TestFile source(String path)

    static class DocumentedProblem {
        final String message
        final Severity severity
        final String id
        final String section
        final boolean defaultDocLink
        final ValidationMessageDisplayConfiguration config

        DocumentedProblem(ValidationMessageDisplayConfiguration config, Severity severity, String id = "incremental_build", String section = "") {
            this.config = config
            this.message = config.render()
            this.severity = severity
            this.id = id
            this.section = section
            this.defaultDocLink = (id == "incremental_build") && (section == "")
        }

        DocumentedProblem(String message, Severity severity, String id = "incremental_build", String section = "") {
            this.config = null
            this.message = message
            this.severity = severity
            this.id = id
            this.section = section
            this.defaultDocLink = (id == "incremental_build") && (section == "")
        }

    }
}
