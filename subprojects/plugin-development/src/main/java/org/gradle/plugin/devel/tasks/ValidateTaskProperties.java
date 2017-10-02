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
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.DocumentationRegistry;
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
import org.gradle.api.tasks.TaskValidationException;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validates task property annotations.
 *
 * <p>
 *     Task properties must be annotated with one of:
 * </p>
 *
 * <ul>
 *     <li>
 *         <b>Properties taken into account during up-to-date checks:</b>
 *         <ul>
*              <li>
 *                 {@literal @}{@link org.gradle.api.tasks.Input},
 *                 {@literal @}{@link org.gradle.api.tasks.Nested},
 *                 {@literal @}{@link org.gradle.api.tasks.InputFile},
 *                 {@literal @}{@link org.gradle.api.tasks.InputDirectory},
 *                 {@literal @}{@link org.gradle.api.tasks.InputFiles}
 *                 to mark it as an input to the task.
 *             </li>
 *             <li>
 *                 {@literal @}{@link org.gradle.api.tasks.OutputFile},
 *                 {@literal @}{@link org.gradle.api.tasks.OutputDirectory}
 *                 to mark it as an output of the task.
 *             </li>
 *         </ul>
 *    </li>
 *    <li>
 *         <b>Properties ignored during up-to-date checks:</b>
 *         <ul>
 *             <li>{@literal @}{@link javax.inject.Inject} marks a Gradle service used by the task.</li>
 *             <li>{@literal @}{@link org.gradle.api.tasks.Console Console} marks a property that only influences the console output of the task.</li>
 *             <li>{@literal @}{@link org.gradle.api.tasks.Internal Internal} mark an internal property of the task.</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * @since 3.0
 */
@Incubating
@CacheableTask
@SuppressWarnings("WeakerAccess")
public class ValidateTaskProperties extends ConventionTask implements VerificationTask {
    private FileCollection classes;
    private FileCollection classpath;
    private Object outputFile;
    private boolean ignoreFailures;
    private boolean failOnWarning;

    @TaskAction
    public void validateTaskClasses() throws IOException {
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassPath classPath = new DefaultClassPath(Iterables.concat(getClasses(), getClasspath()));
        ClassLoader classLoader = getClassLoaderFactory().createIsolatedClassLoader(classPath);
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
        final Class<?> taskInterface;
        final Method validatorMethod;
        try {
            taskInterface = classLoader.loadClass(Task.class.getName());
            Class<?> validatorClass = classLoader.loadClass("org.gradle.api.internal.project.taskfactory.TaskPropertyValidationAccess");
            validatorMethod = validatorClass.getMethod("collectTaskValidationProblems", Class.class, Map.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
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
                    } catch (IllegalAccessError e) {
                        throw new GradleException("Could not load class: " + className, e);
                    } catch (ClassNotFoundException e) {
                        throw new GradleException("Could not load class: " + className, e);
                    } catch (NoClassDefFoundError e) {
                        throw new GradleException("Could not load class: " + className, e);
                    }
                    if (!Modifier.isPublic(clazz.getModifiers())) {
                        continue;
                    }
                    if (Modifier.isAbstract(clazz.getModifiers())) {
                        continue;
                    }
                    if (!taskInterface.isAssignableFrom(clazz)) {
                        continue;
                    }
                    Class<? extends Task> taskClass = Cast.uncheckedCast(clazz);
                    try {
                        validatorMethod.invoke(null, taskClass, taskValidationProblems);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        List<String> problemMessages = toProblemMessages(taskValidationProblems);
        storeResults(problemMessages, getOutputFile());
        communicateResult(problemMessages, taskValidationProblems.values().contains(Boolean.TRUE));
    }

    private void storeResults(List<String> problemMessages, File outputFile) throws IOException {
        if (outputFile != null) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.createNewFile();
            Files.asCharSink(outputFile, Charsets.UTF_8).write(Joiner.on('\n').join(problemMessages));
        }
    }

    private void communicateResult(List<String> problemMessages, boolean hasErrors) {
        if (problemMessages.isEmpty()) {
            getLogger().info("Task property validation finished without warnings.");
        } else {
            if (hasErrors || getFailOnWarning()) {
                if (getIgnoreFailures()) {
                    getLogger().warn("Task property validation finished with errors. See {} for more information on how to annotate task properties.{}", getDocumentationRegistry().getDocumentationFor("more_about_tasks", "sec:task_input_output_annotations"), toMessageList(problemMessages));
                } else {
                    throw new TaskValidationException(String.format("Task property validation failed. See %s for more information on how to annotate task properties.", getDocumentationRegistry().getDocumentationFor("more_about_tasks", "sec:task_input_output_annotations")), toExceptionList(problemMessages));
                }
            } else {
                getLogger().warn("Task property validation finished with warnings:{}", toMessageList(problemMessages));
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
        return  Lists.transform(problemMessages, new Function<String, InvalidUserDataException>() {
            @Override
            public InvalidUserDataException apply(String problemMessage) {
                return new InvalidUserDataException(problemMessage);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The classes to validate.
     *
     * @since 4.0
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    @SkipWhenEmpty
    public FileCollection getClasses() {
        return classes;
    }

    /**
     * Sets the classes to validate.
     *
     * @since 4.0
     */
    public void setClasses(FileCollection classes) {
        this.classes = classes;
    }

    /**
     * The classpath used to load the classes under validation.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Sets the classpath used to load the classes under validation.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns whether the build should break when the verifications performed by this task detects a warning.
     */
    @Input
    public boolean getFailOnWarning() {
        return failOnWarning;
    }

    /**
     * Returns the output file to store the report in.
     */
    @Optional @OutputFile
    public File getOutputFile() {
        return outputFile == null ? null : getProject().file(outputFile);
    }

    /**
     * Sets the output file to store the report in.
     *
     * @since 4.0
     */
    public void setOutputFile(File outputFile) {
        setOutputFile((Object) outputFile);
    }

    /**
     * Sets the output file to store the report in.
     */
    public void setOutputFile(Object outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Specifies whether the build should break when the verifications performed by this task detects a warning.
     *
     * @param failOnWarning {@code true} to break the build on warning, {@code false} to ignore warnings. The default is {@code false}.
     */
    @SuppressWarnings("unused")
    public void setFailOnWarning(boolean failOnWarning) {
        this.failOnWarning = failOnWarning;
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
            super(Opcodes.ASM6);
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
