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
import org.gradle.internal.reflect.TypeValidationContext
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import javax.inject.Inject

import static org.gradle.internal.reflect.TypeValidationContext.Severity.ERROR
import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING

abstract class AbstractPluginValidationIntegrationSpec extends AbstractIntegrationSpec {

    def "detects missing annotations on Java properties"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'badTime' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'oldThing' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'options.badNested' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'ter' is not annotated with an input or output annotation.": WARNING,
        )
    }

    @Unroll
    def "task can have property with annotation @#annotation.simpleName"() {
        file("input.txt").text = "input"
        file("input").createDir()

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;

            import java.io.File;
            import java.util.*;

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

    @Unroll
    def "detects optional primitive type #type"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MyTask extends DefaultTask {
                @Optional @Input
                ${type.name} getPrimitive() {
                    return ${value};
                }

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'primitive' @Input properties with primitive type '${type.name}' cannot be @Optional.": WARNING,
        )

        where:
        type    | value
        boolean | true
        int     | 1
        double  | 1
    }

    def "validates task caching annotations"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;

            @CacheableTransform
            public class MyTask extends DefaultTask {
                @Nested
                Options getOptions() {
                    return new Options();
                }

                @CacheableTask @CacheableTransform
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
        assertValidationFailsWith(true, [
            "Type 'MyTask': Cannot use @CacheableTransform on type. This annotation can only be used with TransformAction types.": ERROR,
            "Type 'MyTask.Options': Cannot use @CacheableTask on type. This annotation can only be used with Task types.": ERROR,
            "Type 'MyTask.Options': Cannot use @CacheableTransform on type. This annotation can only be used with TransformAction types.": ERROR,
        ])
    }

    def "detects missing annotation on Groovy properties"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'badTime' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'options.badNested' is not annotated with an input or output annotation.": WARNING,
        )
    }

    def "no problems with Copy task"() {
        file("input.txt").text = "input"

        javaTaskSource << """
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

            import java.io.File;
            import java.util.concurrent.Callable;

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
                private final Boolean enabled;

                public Pojo(Boolean enabled) {
                    this.enabled = enabled;
                }

                @Input
                public Boolean isEnabled() {
                    return enabled;
                }
            }
        """

        expect:
        assertValidationSucceeds()
    }

    @Unroll
    def "reports setters for property of mutable type #type"() {
        file("input.txt").text = "input"

        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MyTask extends DefaultTask {
                private final ${type} mutableProperty = ${init};

                // getter and setter
                @InputFiles @PathSensitive(PathSensitivity.NONE)
                public ${type} getMutablePropertyWithSetter() { return mutableProperty; }
                public void setMutablePropertyWithSetter(${type} value) {}

                // just getter
                @InputFiles @PathSensitive(PathSensitivity.NONE)
                public ${type} getMutablePropertyWithoutSetter() { return mutableProperty; }

                // just setter
                // TODO implement warning for this case: https://github.com/gradle/gradle/issues/9341
                public void setMutablePropertyWithoutGetter() {}

                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'mutablePropertyWithSetter' of mutable type '${type.replaceAll("<.+>", "")}' is writable. Properties of this type should be read-only and mutated via the value itself.": WARNING,
        )

        where:
        type                            | init
        ConfigurableFileCollection.name | "getProject().getObjects().fileCollection()"
        "${Property.name}<String>"      | "getProject().getObjects().property(String.class).convention(\"value\")"
        RegularFileProperty.name        | "getProject().getObjects().fileProperty().fileValue(new java.io.File(\"input.txt\"))"
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

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'file' has @Input annotation used on property of type 'File'.": WARNING,
            "Type 'MyTask': property 'fileCollection' has @Input annotation used on property of type 'FileCollection'.": WARNING,
            "Type 'MyTask': property 'filePath' has @Input annotation used on property of type 'Path'.": WARNING,
            "Type 'MyTask': property 'fileTree' has @Input annotation used on property of type 'FileTree'.": WARNING,
            "Type 'MyTask': property 'inputDirectory' is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE.": WARNING,
            "Type 'MyTask': property 'inputFile' is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE.": WARNING,
            "Type 'MyTask': property 'inputFiles' is declared without normalization specified. Properties of cacheable work must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath. Defaulting to PathSensitivity.ABSOLUTE.": WARNING,
        )
    }

    def "detects problems on nested collections"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import java.util.*;
            import java.io.File;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'doubleIterableOptions${iterableSymbol}${iterableSymbol}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'iterableMappedOptions${iterableSymbol}${getKeySymbolFor("alma")}${iterableSymbol}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'iterableOptions${iterableSymbol}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'mappedOptions${getKeySymbolFor("alma")}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'namedIterable${getNameSymbolFor("tibor")}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'options.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'optionsList${iterableSymbol}.notAnnotated' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'providedOptions.notAnnotated' is not annotated with an input or output annotation.": WARNING,
        )
    }

    def "detects annotations on private getter methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import java.io.File;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'badTime' is private and annotated with @Input.": WARNING,
            "Type 'MyTask': property 'options.badNested' is private and annotated with @Input.": WARNING,
            "Type 'MyTask': property 'outputDir' is private and annotated with @OutputDirectory.": WARNING,
        )
    }

    def "detects annotations on non-property methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import java.io.File;

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
                }

                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': non-property method 'notAGetter()' should not be annotated with: @Input.": WARNING,
            "Type 'MyTask.Options': non-property method 'notANestedGetter()' should not be annotated with: @Input.": WARNING,
        )
    }

    def "detects annotations on setter methods"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import java.io.File;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'readWrite' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': setter method 'setReadWrite()' should not be annotated with: @Input.": WARNING,
            "Type 'MyTask': setter method 'setWriteOnly()' should not be annotated with: @Input.": WARNING,
            "Type 'MyTask.Options': setter method 'setReadWrite()' should not be annotated with: @Input.": WARNING,
            "Type 'MyTask.Options': setter method 'setWriteOnly()' should not be annotated with: @Input.": WARNING,
        )
    }

    def "reports conflicting types when property is replaced"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.*;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'oldProperty' annotated with @ReplacedBy should not be also annotated with @Input.": WARNING,
        )
    }

    def "reports both input and output annotation applied to the same property"() {
        javaTaskSource << """
            import java.io.File;
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.*;

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
        assertValidationFailsWith(
            "Type 'MyTask': property 'file' has conflicting type annotations declared: @InputFile, @OutputFile; assuming @InputFile.": WARNING,
        )
    }

    abstract String getIterableSymbol()

    abstract String getNameSymbolFor(String name)

    abstract String getKeySymbolFor(String name)

    abstract void assertValidationSucceeds()

    abstract void assertValidationFailsWith(boolean expectDeprecationsForErrors = false, Map<String, TypeValidationContext.Severity> messages)

    abstract TestFile source(String path)

    TestFile getJavaTaskSource() {
        source("src/main/java/MyTask.java")
    }

    TestFile getGroovyTaskSource() {
        buildFile << """
            apply plugin: "groovy"
        """
        source("src/main/groovy/MyTask.groovy")
    }
}
