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

package org.gradle.plugin.devel.tasks.internal;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.api.problems.interfaces.Severity.WARNING;
import static org.gradle.internal.deprecation.Documentation.userManual;

public abstract class ValidateAction implements WorkAction<ValidateAction.Params> {
    private final static Logger LOGGER = Logging.getLogger(ValidateAction.class);
    public static final String PROBLEM_SEPARATOR = "--------";

    public interface Params extends WorkParameters {
        ConfigurableFileCollection getClasses();

        RegularFileProperty getOutputFile();

        Property<Boolean> getEnableStricterValidation();

    }

    @Override
    public void execute() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<Problem> taskValidationProblems = new ArrayList<>();

        Params params = getParameters();

        params.getClasses().getAsFileTree().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                if (!fileDetails.getPath().endsWith(".class")) {
                    return;
                }
                List<String> classNames = getClassNames(fileDetails);
                for (String className : classNames) {
                    Class<?> clazz;
                    try {
                        clazz = classLoader.loadClass(className);
                    } catch (IncompatibleClassChangeError | NoClassDefFoundError | VerifyError | ClassNotFoundException e) {
                        LOGGER.debug("Could not load class: " + className, e);
                        continue;
                    }
                    collectValidationProblems(null, clazz, taskValidationProblems, params.getEnableStricterValidation().get());
                }
            }

            @Nonnull
            private List<String> getClassNames(FileVisitDetails fileDetails) {
                ClassReader reader = createClassReader(fileDetails);
                List<String> classNames = Lists.newArrayList();
                reader.accept(new TaskNameCollectorVisitor(classNames), ClassReader.SKIP_CODE);
                return classNames;
            }

            @Nonnull
            private ClassReader createClassReader(FileVisitDetails fileDetails) {
                try {
                    return new ClassReader(Files.asByteSource(fileDetails.getFile()).read());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        storeResults(taskValidationProblems, params.getOutputFile());
    }

    private static void collectValidationProblems(Problems problemService, Class<?> topLevelBean, List<Problem> problems, boolean enableStricterValidation) {
        DefaultTypeValidationContext validationContext = createTypeValidationContext(problemService, topLevelBean, enableStricterValidation);
        PropertyValidationAccess.collectValidationProblems(topLevelBean, validationContext);

        problems.addAll(validationContext.getProblems());
    }

    @Nonnull
    private static DefaultTypeValidationContext createTypeValidationContext(Problems problemService, Class<?> topLevelBean, boolean enableStricterValidation) {
        if (Task.class.isAssignableFrom(topLevelBean)) {
            return createValidationContextAndValidateCacheableAnnotations(problemService, topLevelBean, CacheableTask.class, enableStricterValidation);
        }
        if (TransformAction.class.isAssignableFrom(topLevelBean)) {
            return createValidationContextAndValidateCacheableAnnotations(problemService, topLevelBean, CacheableTransform.class, enableStricterValidation);
        }
        return createValidationContext(problemService, topLevelBean, enableStricterValidation);
    }

    private static DefaultTypeValidationContext createValidationContextAndValidateCacheableAnnotations(Problems problems, Class<?> topLevelBean, Class<? extends Annotation> cacheableAnnotationClass, boolean enableStricterValidation) {
        boolean cacheable = topLevelBean.isAnnotationPresent(cacheableAnnotationClass);
        DefaultTypeValidationContext validationContext = createValidationContext(problems, topLevelBean, cacheable || enableStricterValidation);
        if (enableStricterValidation) {
            validateCacheabilityAnnotationPresent(topLevelBean, cacheable, cacheableAnnotationClass, validationContext);
        }
        return validationContext;
    }

    private static DefaultTypeValidationContext createValidationContext(Problems problems, Class<?> topLevelBean, boolean reportCacheabilityProblems) {
        return DefaultTypeValidationContext.withRootType(problems, topLevelBean, reportCacheabilityProblems);
    }

    private static void validateCacheabilityAnnotationPresent(Class<?> topLevelBean, boolean cacheable, Class<? extends Annotation> cacheableAnnotationClass, DefaultTypeValidationContext validationContext) {
        if (topLevelBean.isInterface()) {
            // Won't validate interfaces
            return;
        }
        if (!cacheable
            && topLevelBean.getAnnotation(DisableCachingByDefault.class) == null
            && topLevelBean.getAnnotation(UntrackedTask.class) == null
        ) {
            boolean isTask = Task.class.isAssignableFrom(topLevelBean);
            String cacheableAnnotation = "@" + cacheableAnnotationClass.getSimpleName();
            String disableCachingAnnotation = "@" + DisableCachingByDefault.class.getSimpleName();
            String untrackedTaskAnnotation = "@" + UntrackedTask.class.getSimpleName();
            String workType = isTask ? "task" : "transform action";
            validationContext.visitTypeProblem(problem -> {
                    ProblemBuilder builder = problem
                        .withAnnotationType(topLevelBean)
                        .label("must be annotated either with " + cacheableAnnotation + " or with " + disableCachingAnnotation)
                        .documentedAt(userManual("validation_problems", "disable_caching_by_default"))
                        .noLocation()
                        .type(ValidationProblemId.NOT_CACHEABLE_WITHOUT_REASON.name())
                        .group(ProblemGroup.TYPE_VALIDATION_ID)
                        .severity(WARNING)
                        .description("The " + workType + " author should make clear why a " + workType + " is not cacheable")
                        .solution("Add " + disableCachingAnnotation + "(because = ...)")
                        .solution("Add " + cacheableAnnotation);
                    if (isTask) {
                        builder.solution("Add " + untrackedTaskAnnotation + "(because = ...)");
                    }
                }
            );
        }
    }

    private static void storeResults(List<Problem> problemMessages, RegularFileProperty outputFile) {
        if (outputFile.isPresent()) {
            File output = outputFile.get().getAsFile();
            try {
                //noinspection ResultOfMethodCallIgnored
                output.createNewFile();
                Gson gson = ValidationProblemSerialization.createGsonBuilder().create();
                Files.asCharSink(output, Charsets.UTF_8).write(gson.toJson(problemMessages));
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }
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
