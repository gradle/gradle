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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import spock.lang.Issue

class RuntimePluginValidationIntegrationTest extends AbstractIntegrationSpec implements RuntimePluginValidationTrait, ValidationMessageChecker {

    def "supports recursive types"() {
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @Nested
                Tree tree = new Tree(
                        left: new Tree([:]),
                        right: new Tree([:])
                    )

                public static class Tree {
                    @Optional @Nested
                    Tree left

                    @Optional @Nested
                    Tree right

                    String nonAnnotated
                }

                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationFailsWith([
            error(missingAnnotationConfig { type('MyTask').property('tree.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('tree.left.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
            error(missingAnnotationConfig { type('MyTask').property('tree.right.nonAnnotated').missingInputOrOutput() }, 'validation_problems', 'missing_annotation'),
        ])
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
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=10")
        assertValidationFailsWith([
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('file').propertyType('File') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('fileCollection').propertyType('FileCollection') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('filePath').propertyType('Path') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            error(incorrectUseOfInputAnnotationConfig { type('MyTask').property('fileTree').propertyType('FileTree') }, 'validation_problems', 'incorrect_use_of_input_annotation'),
            // Pre-Validate errors halt execution before further problems are detected
        ])
    }

    //@IgnoreRest
    def "detects problems on nested collections"() {
        javaTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;
            import java.util.*;
            import java.io.File;
            import javax.inject.Inject;

            @DisableCachingByDefault(because = "test task")
            public abstract class MyTask extends DefaultTask {
                @Inject
                protected abstract ObjectFactory getObjects();

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
                    return getObjects().property(Options.class).convention(new Options());
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
        executer.withArgument("-Dorg.gradle.internal.max.validation.errors=10")
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
    }

    @Issue("https://github.com/gradle/gradle/issues/24444")
    def "value not set because it is derived from a property whose value cannot be configured"() {
        groovyTaskSource << """
            import org.gradle.api.*;
            import org.gradle.api.model.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            abstract class MyTask extends DefaultTask {

                @Input
                @Optional
                abstract Property<String> getGreeting();

                @Input
                Provider<String> message = greeting.map(it -> it + " from Gradle");

                @TaskAction
                void printMessage() {
                    logger.quiet(message().get());
                }
            }
        """

        expect:
        fails "run"
        failure.assertHasDescription("A problem was found with the configuration of task ':run' (type 'MyTask').")
        failureDescriptionContains(missingNonConfigurableValueMessage({ type('MyTask').property('message') }))
    }
}
