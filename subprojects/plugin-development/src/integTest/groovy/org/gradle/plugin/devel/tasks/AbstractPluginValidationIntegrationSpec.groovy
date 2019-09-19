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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import javax.inject.Inject

import static org.gradle.plugin.devel.tasks.AbstractPluginValidationIntegrationSpec.Severity.ERROR
import static org.gradle.plugin.devel.tasks.AbstractPluginValidationIntegrationSpec.Severity.STRICT_WARNING
import static org.gradle.plugin.devel.tasks.AbstractPluginValidationIntegrationSpec.Severity.WARNING

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
                @${application}
                public ${type.name} getThing() {
                    return ${value};
                }
                
                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationSucceeds()

        where:
        annotation        | application                   | type           | value
        Inject            | Inject.name                   | ObjectFactory  | null
        OptionValues      | "${OptionValues.name}(\"a\")" | List           | null
        Internal          | 'Internal'                    | String         | null
        ReplacedBy        | 'ReplacedBy("")'              | String         | null
        Console           | 'Console'                     | Boolean        | null
        Destroys          | 'Destroys'                    | FileCollection | null
        LocalState        | 'LocalState'                  | FileCollection | null
        InputFile         | 'InputFile'                   | File           | "new File(\"input.txt\")"
        InputFiles        | 'InputFiles'                  | Set            | "new HashSet()"
        InputDirectory    | 'InputDirectory'              | File           | "new File(\"input\")"
        Input             | 'Input'                       | String         | "\"value\""
        OutputFile        | 'OutputFile'                  | File           | "new File(\"output.txt\")"
        OutputFiles       | 'OutputFiles'                 | Map            | "new HashMap<String, File>()"
        OutputDirectory   | 'OutputDirectory'             | File           | "new File(\"output\")"
        OutputDirectories | 'OutputDirectories'           | Map            | "new HashMap<String, File>()"
        Nested            | 'Nested'                      | List           | "new ArrayList()"
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
        assertValidationFailsWith(
            "Cannot use @CacheableTask with type MyTask.Options. This annotation can only be used with Task types.": ERROR,
            "Cannot use @CacheableTransform with type MyTask. This annotation can only be used with TransformAction types.": ERROR,
            "Cannot use @CacheableTransform with type MyTask.Options. This annotation can only be used with TransformAction types.": ERROR,
        )
    }

    def "detects missing annotation on Groovy properties"() {
        buildFile << """
            apply plugin: "groovy"

            dependencies {
                implementation localGroovy()
            }
        """
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
    def "report setters for property of mutable type #type"() {
        file("input.txt").text = "input"

        javaTaskSource << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.InputFiles;
            import org.gradle.api.tasks.TaskAction;

            public class MyTask extends DefaultTask {
                private final ${type} mutableProperty = ${init};

                // getter and setter
                @InputFiles public ${type} getMutablePropertyWithSetter() { return mutableProperty; } 
                public void setMutablePropertyWithSetter(${type} value) {} 

                // just getter
                @InputFiles public ${type} getMutablePropertyWithoutSetter() { return mutableProperty; } 

                // just setter
                // TODO implement warning for this case: https://github.com/gradle/gradle/issues/9341
                public void setMutablePropertyWithoutGetter() {}
                
                @TaskAction public void execute() {}
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'mutablePropertyWithSetter' of mutable type '${type.replaceAll("<.+>", "")}' is writable. Properties of this type should be read-only and mutated via the value itself": WARNING,
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
            import org.gradle.api.tasks.*;
            import java.util.Set;
            import java.util.Collections;
            import java.io.File;

            @CacheableTask
            public class MyTask extends DefaultTask {
                @Input
                public long getGoodTime() {
                    return 0;
                }

                @Nested
                public Options getOptions() {
                    return new Options();
                }

                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver;

                public long getBadTime() {
                    return 0;
                }

                public static class Options {
                    @Input
                    public String getGoodNested() {
                        return "good";
                    }
                    public String getBadNested(){
                        return "bad";
                    }
                }
                
                @OutputDirectory
                public File getOutputDir() {
                    return new File("outputDir");
                }
                
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
                
                @TaskAction
                public void doStuff() { }
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'badTime' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'file' has @Input annotation used on property of type java.io.File.": WARNING,
            "Type 'MyTask': property 'inputDirectory' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.": STRICT_WARNING,
            "Type 'MyTask': property 'inputFile' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.": STRICT_WARNING,
            "Type 'MyTask': property 'inputFiles' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE.": STRICT_WARNING,
            "Type 'MyTask': property 'options.badNested' is not annotated with an input or output annotation.": WARNING,
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
            "Type 'MyTask\$Options': non-property method 'notANestedGetter()' should not be annotated with: @Input": WARNING,
            "Type 'MyTask': non-property method 'notAGetter()' should not be annotated with: @Input": WARNING,
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
            "Type 'MyTask\$Options': setter method 'setReadWrite()' should not be annotated with: @Input": WARNING,
            "Type 'MyTask\$Options': setter method 'setWriteOnly()' should not be annotated with: @Input": WARNING,
            "Type 'MyTask': property 'readWrite' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': setter method 'setReadWrite()' should not be annotated with: @Input": WARNING,
            "Type 'MyTask': setter method 'setWriteOnly()' should not be annotated with: @Input": WARNING,
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
            "Type 'MyTask': property 'oldProperty' getter 'getOldProperty()' annotated with @ReplacedBy should not be also annotated with @Input.": WARNING,
        )
    }

    enum Severity {
        /**
         * A validation warning, emitted as a deprecation warning during runtime.
         */
        WARNING("Warning"),

        /**
         * A validation warning emitted only when strict mode is enabled (never during runtime).
         */
        STRICT_WARNING("Warning"),

        /**
         * A validation error, emitted as a failure cause during runtime.
         */
        ERROR("Error"),

        /**
         * A validation error emitted only when strict mode is enabled (never during runtime).
         */
        STRICT_ERROR("Error");

        private final String displayName

        Severity(String displayName) {
            this.displayName = displayName
        }

        @Override
        String toString() {
            return displayName
        }
    }

    abstract void assertValidationSucceeds()

    abstract void assertValidationFailsWith(Map<String, Severity> messages)

    abstract TestFile source(String path)

    TestFile getJavaTaskSource() {
        source("src/main/java/MyTask.java")
    }

    TestFile getGroovyTaskSource() {
        source("src/main/groovy/MyTask.groovy")
    }
}
