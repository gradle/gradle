/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.io.Files;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderDefiningLocation;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.gradle.api.problems.Severity.WARNING;
import static org.gradle.internal.deprecation.Documentation.userManual;

class ClassValidator extends EmptyFileVisitor {

    private final static Logger LOGGER = Logging.getLogger(ClassValidator.class);
    private final ClassLoader classLoader;
    private final List<Problem> taskValidationProblems = new ArrayList<>();
    private final ValidateAction.Params params;

    public ClassValidator(ClassLoader classLoader, ValidateAction.Params params) {
        this.classLoader = classLoader;
        this.params = params;
    }

    @Override
    public void visitFile(FileVisitDetails fileDetails) {
        if (!fileDetails.getPath().endsWith(".class")) {
            return;
        }
        List<String> classNames = getClassNames(fileDetails);
        for (String className : classNames) {
            getClassForName(className)
                .ifPresent(clazz ->
                    collectValidationProblems(clazz, taskValidationProblems, params.getEnableStricterValidation().get(), getSourceFileForClass(className))
                );
        }
    }

    Optional<Class<?>> getClassForName(String className) {
        try {
            return ofNullable(classLoader.loadClass(className.replace('/', '.')));
        } catch (IncompatibleClassChangeError | NoClassDefFoundError | VerifyError | ClassNotFoundException e) {
            LOGGER.debug("Could not load class: " + className, e);
            return empty();
        }
    }

    private static void collectValidationProblems(Class<?> topLevelBean, List<Problem> problems, boolean enableStricterValidation, Optional<File> sourceFileForClass) {
        DefaultTypeValidationContext validationContext = createTypeValidationContext(topLevelBean, enableStricterValidation, sourceFileForClass);
        PropertyValidationAccess.collectValidationProblems(topLevelBean, validationContext);

        problems.addAll(validationContext.getProblems());
    }

    @Nonnull
    private static DefaultTypeValidationContext createTypeValidationContext(Class<?> topLevelBean, boolean enableStricterValidation, Optional<File> sourceFileForClass) {
        if (Task.class.isAssignableFrom(topLevelBean)) {
            return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTask.class, enableStricterValidation, sourceFileForClass);
        }
        if (TransformAction.class.isAssignableFrom(topLevelBean)) {
            return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTransform.class, enableStricterValidation, sourceFileForClass);
        }
        return createValidationContext(topLevelBean, enableStricterValidation);
    }

    private static DefaultTypeValidationContext createValidationContextAndValidateCacheableAnnotations(Class<?> topLevelBean, Class<? extends Annotation> cacheableAnnotationClass, boolean enableStricterValidation, Optional<File> sourceFileForClass) {
        boolean cacheable = topLevelBean.isAnnotationPresent(cacheableAnnotationClass);
        DefaultTypeValidationContext validationContext = createValidationContext(topLevelBean, cacheable || enableStricterValidation);
        if (enableStricterValidation) {
            validateCacheabilityAnnotationPresent(topLevelBean, cacheable, cacheableAnnotationClass, validationContext, sourceFileForClass);
        }
        return validationContext;
    }

    private static DefaultTypeValidationContext createValidationContext(Class<?> topLevelBean, boolean reportCacheabilityProblems) {
        return DefaultTypeValidationContext.withRootType(topLevelBean, reportCacheabilityProblems);
    }

    private static void validateCacheabilityAnnotationPresent(Class<?> topLevelBean, boolean cacheable, Class<? extends Annotation> cacheableAnnotationClass, DefaultTypeValidationContext validationContext, Optional<File> sourceFileForClass) {
        if (topLevelBean.isInterface()
            || cacheable
            || topLevelBean.getAnnotation(DisableCachingByDefault.class) != null
            || topLevelBean.getAnnotation(UntrackedTask.class) != null) {
            // Won't validate interfaces
            return;
        }

        boolean isTask = Task.class.isAssignableFrom(topLevelBean);
        String cacheableAnnotation = "@" + cacheableAnnotationClass.getSimpleName();
        String disableCachingAnnotation = "@" + DisableCachingByDefault.class.getSimpleName();
        String untrackedTaskAnnotation = "@" + UntrackedTask.class.getSimpleName();
        String workType = isTask ? "task" : "transform action";
        validationContext.visitTypeProblem(problem -> {
                ProblemBuilderDefiningLocation problemBuilderDefiningLocation = problem
                    .withAnnotationType(topLevelBean)
                    .label("must be annotated either with " + cacheableAnnotation + " or with " + disableCachingAnnotation)
                    .documentedAt(userManual("validation_problems", "disable_caching_by_default"));
                ProblemBuilder builder = sourceFileForClass.map(file -> problemBuilderDefiningLocation.location(file.getAbsolutePath(), null))
                    .orElseGet(problemBuilderDefiningLocation::noLocation).category(ValidationTypes.NOT_CACHEABLE_WITHOUT_REASON)
                    .severity(WARNING)
                    .details("The " + workType + " author should make clear why a " + workType + " is not cacheable")
                    .solution("Add " + disableCachingAnnotation + "(because = ...)")
                    .solution("Add " + cacheableAnnotation);
                if (isTask) {
                    builder.solution("Add " + untrackedTaskAnnotation + "(because = ...)");
                }
            }
        );
    }

    @Nonnull
    private Optional<File> getSourceFileForClass(String className) {
        return params.getSourceSet().get()
            .filter(file -> file.getAbsolutePath().endsWith(className + ".java"))
            .getFiles()
            .stream()
            .findFirst();
    }

    @Nonnull
    private static List<String> getClassNames(FileVisitDetails fileDetails) {
        ClassReader reader = createClassReader(fileDetails);
        TaskNameCollectorVisitor collectorVisitor = new TaskNameCollectorVisitor();
        reader.accept(collectorVisitor, ClassReader.SKIP_CODE);
        return collectorVisitor.getClassNames();
    }

    @Nonnull
    private static ClassReader createClassReader(FileVisitDetails fileDetails) {
        try {
            return new ClassReader(Files.asByteSource(fileDetails.getFile()).read());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Problem> getTaskValidationProblems() {
        return taskValidationProblems;
    }
}
