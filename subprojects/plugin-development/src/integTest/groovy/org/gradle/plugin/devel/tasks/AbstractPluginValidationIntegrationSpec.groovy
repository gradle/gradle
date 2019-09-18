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

import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.model.ReplacedBy
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
import static org.gradle.plugin.devel.tasks.AbstractPluginValidationIntegrationSpec.Severity.WARNING

abstract class AbstractPluginValidationIntegrationSpec extends AbstractIntegrationSpec {

    def "detects missing annotations on Java properties"() {
        source << """
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

        source << """
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

    @Unroll
    def "task cannot have property with annotation @#annotation.simpleName"() {
        source << """
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
        assertValidationFailsWith(
            "Type 'MyTask': property 'options.nestedThing' is annotated with invalid property type @${annotation.simpleName}.": ERROR,
            "Type 'MyTask': property 'thing' is annotated with invalid property type @${annotation.simpleName}.": ERROR,
        )

        where:
        annotation                | _
        InputArtifact             | _
        InputArtifactDependencies | _
    }

    def "validates task caching annotations"() {
        source << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.artifacts.transform.*;

            @CacheableTransform 
            public class MyTask extends DefaultTask {
                @Nested
                Options getOptions() { 
                    return null;
                }

                @CacheableTask @CacheableTransform 
                public static class Options {
                    @Input
                    String getNestedThing() {
                        return null;
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

    enum Severity {
        WARNING("Warning"), ERROR("Error");

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

    TestFile getSource() {
        source("java/MyTask.java")
    }
}
