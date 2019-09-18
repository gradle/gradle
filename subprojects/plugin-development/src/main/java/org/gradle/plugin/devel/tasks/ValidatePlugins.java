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

package org.gradle.plugin.devel.tasks;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.WorkValidationException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Validates plugins by checking property annotations on work items like tasks and artifact transforms.
 *
 * See the user guide for more information on
 * <a href="https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks" target="_top">incremental build</a> and
 * <a href="https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching" target="_top">caching task outputs</a>.
 *
 * @since 6.0
 */
@CacheableTask
@Incubating
public class ValidatePlugins extends DefaultTask {
    private final ConfigurableFileCollection classes;
    private final ConfigurableFileCollection classpath;
    private final RegularFileProperty outputFile;
    private final Property<Boolean> enableStricterValidation;
    private final Property<Boolean> ignoreFailures;
    private final Property<Boolean> failOnWarning;

    @Inject
    public ValidatePlugins(ObjectFactory objects) {
        this.classes = objects.fileCollection();
        this.classpath = objects.fileCollection();
        this.outputFile = objects.fileProperty();
        this.enableStricterValidation = objects.property(Boolean.class).convention(false);
        this.ignoreFailures = objects.property(Boolean.class).convention(false);
        this.failOnWarning = objects.property(Boolean.class).convention(true);
    }

    @TaskAction
    public void validateTaskClasses() throws IOException {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassPath classPath = DefaultClassPath.of(Iterables.concat(getClasses(), getClasspath()));
        ClassLoader classLoader = getClassLoaderFactory().createIsolatedClassLoader("task-loader", classPath);
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            validateTaskClasses(classLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            ClassLoaderUtils.tryClose(classLoader);
        }
    }

    private void validateTaskClasses(final ClassLoader classLoader) throws IOException {
        final Map<String, Boolean> taskValidationProblems = Maps.newTreeMap();
        final Method validatorMethod;
        try {
            Class<?> validatorClass = classLoader.loadClass("org.gradle.api.internal.tasks.properties.PropertyValidationAccess");
            validatorMethod = validatorClass.getMethod("collectValidationProblems", Class.class, Map.class, Boolean.TYPE);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        getClasses().getAsFileTree().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                if (!fileDetails.getPath().endsWith(".class")) {
                    return;
                }
                ClassReader reader;
                try {
                    reader = new ClassReader(Files.asByteSource(fileDetails.getFile()).read());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                List<String> classNames = Lists.newArrayList();
                reader.accept(new TaskNameCollectorVisitor(classNames), ClassReader.SKIP_CODE);
                for (String className : classNames) {
                    Class<?> clazz;
                    try {
                        clazz = classLoader.loadClass(className);
                    } catch (IllegalAccessError | NoClassDefFoundError | ClassNotFoundException e) {
                        throw new GradleException("Could not load class: " + className, e);
                    }
                    try {
                        validatorMethod.invoke(null, clazz, taskValidationProblems, enableStricterValidation.get());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        List<String> problemMessages = toProblemMessages(taskValidationProblems);
        storeResults(problemMessages);
        communicateResult(problemMessages, taskValidationProblems.containsValue(Boolean.TRUE));
    }

    private void storeResults(List<String> problemMessages) throws IOException {
        if (outputFile.isPresent()) {
            File output = outputFile.get().getAsFile();
            //noinspection ResultOfMethodCallIgnored
            output.createNewFile();
            Files.asCharSink(output, Charsets.UTF_8).write(Joiner.on('\n').join(problemMessages));
        }
    }

    private void communicateResult(List<String> problemMessages, boolean hasErrors) {
        if (problemMessages.isEmpty()) {
            getLogger().info("Plugin validation finished without warnings.");
        } else {
            if (hasErrors || failOnWarning.get()) {
                if (ignoreFailures.get()) {
                    getLogger().warn("Plugin validation finished with errors. See {} for more information on how to annotate task properties.{}", getDocumentationRegistry().getDocumentationFor("more_about_tasks", "sec:task_input_output_annotations"), toMessageList(problemMessages));
                } else {
                    throw new WorkValidationException(String.format("Plugin validation failed. See %s for more information on how to annotate task properties.", getDocumentationRegistry().getDocumentationFor("more_about_tasks", "sec:task_input_output_annotations")), toExceptionList(problemMessages));
                }
            } else {
                getLogger().warn("Plugin validation finished with warnings:{}", toMessageList(problemMessages));
            }
        }
    }

    private static List<String> toProblemMessages(Map<String, Boolean> problems) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (Map.Entry<String, Boolean> entry : problems.entrySet()) {
            String problem = entry.getKey();
            Boolean error = entry.getValue();
            builder.add(String.format("%s: %s",
                Boolean.TRUE.equals(error) ? "Error" : "Warning",
                problem
            ));
        }
        return builder.build();
    }

    private static CharSequence toMessageList(List<String> problemMessages) {
        StringBuilder builder = new StringBuilder();
        for (String problemMessage : problemMessages) {
            builder.append(String.format("%n  - %s", problemMessage));
        }
        return builder;
    }

    private static List<InvalidUserDataException> toExceptionList(List<String> problemMessages) {
        return problemMessages.stream()
            .map(InvalidUserDataException::new)
            .collect(Collectors.toList());
    }

    /**
     * The classes to validate.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    public ConfigurableFileCollection getClasses() {
        return classes;
    }

    /**
     * The classpath used to load the classes under validation.
     */
    @Classpath
    public ConfigurableFileCollection getClasspath() {
        return classpath;
    }

    /**
     * Specifies whether the build should break when plugin verifications fails.
     */
    @Input
    public Property<Boolean> getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Returns whether the build should break when the verifications performed by this task detects a warning.
     */
    @Input
    public Property<Boolean> getFailOnWarning() {
        return failOnWarning;
    }

    /**
     * Enable the stricter validation for cacheable tasks for all tasks.
     */
    @Input
    public Property<Boolean> getEnableStricterValidation() {
        return enableStricterValidation;
    }

    /**
     * Returns the output file to store the report in.
     */
    @Optional
    @OutputFile
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }

    @Inject
    protected ClassLoaderFactory getClassLoaderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected DocumentationRegistry getDocumentationRegistry() {
        throw new UnsupportedOperationException();
    }

    private static class TaskNameCollectorVisitor extends ClassVisitor {
        private final Collection<String> classNames;

        public TaskNameCollectorVisitor(Collection<String> classNames) {
            super(AsmConstants.ASM_LEVEL);
            this.classNames = classNames;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if ((access & Opcodes.ACC_PUBLIC) != 0) {
                classNames.add(name.replace('/', '.'));
            }
        }
    }
}
