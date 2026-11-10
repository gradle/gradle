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

import com.google.common.io.Files;
import org.gradle.api.Task;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.internal.ProblemBuilderInternal;
import org.gradle.api.problems.internal.ProblemGroupsInternal;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.model.internal.asm.AsmConstants;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.internal.deprecation.Documentation.userManual;

public abstract class ValidateAction implements WorkAction<ValidateAction.Params> {
    private final static Logger LOGGER = Logging.getLogger(ValidateAction.class);

    public interface Params extends WorkParameters {
        ConfigurableFileCollection getClasses();

        ConfigurableFileCollection getSourceDirectories();

        RegularFileProperty getOutputFile();

        Property<Boolean> getEnableStricterValidation();

    }

    @Inject
    public abstract ProblemsInternal getProblems();

    @Override
    public void execute() {
        List<Problem> taskValidationWarnings = new ArrayList<>();
        List<Problem> taskValidationErrors = new ArrayList<>();

        Params params = getParameters();

        params.getClasses().getAsFileTree().visit(new ValidationProblemCollector(taskValidationWarnings, taskValidationErrors, params, getProblems()));

        List<File> sourceRoots = params.getSourceDirectories().getFiles().stream()
            .filter(File::isDirectory)
            .collect(Collectors.toList());

        List<Problem> enrichedWarnings = enrichProblems(taskValidationWarnings, sourceRoots, getProblems());
        List<Problem> enrichedErrors = enrichProblems(taskValidationErrors, sourceRoots, getProblems());

        storeResults(enrichedWarnings, enrichedErrors, params.getOutputFile(), getProblems().getGroups());
    }

    private static List<Problem> enrichProblems(List<Problem> problems, List<File> sourceRoots, ProblemsInternal problemsService) {
        if (sourceRoots.isEmpty() || problems.isEmpty()) {
            return problems;
        }

        Map<String, Optional<File>> resolvedFileCache = new HashMap<>();
        List<Problem> enriched = new ArrayList<>(problems.size());
        for (Problem problem : problems) {
            enriched.add(enrichProblemWithLocation(problem, sourceRoots, problemsService, resolvedFileCache));
        }
        return enriched;
    }

    private static Problem enrichProblemWithLocation(
        Problem problem,
        List<File> sourceRoots,
        ProblemsInternal problemsService,
        Map<String, Optional<File>> resolvedFileCache
    ) {
        if (!(problem instanceof ProblemInternal)) {
            return problem;
        }
        ProblemInternal problemInternal = (ProblemInternal) problem;
        AdditionalData additionalData = problemInternal.getAdditionalData();
        if (!(additionalData instanceof TypeValidationData)) {
            return problem;
        }
        TypeValidationData typeValidationData = (TypeValidationData) additionalData;
        String declaringClassName = typeValidationData.getDeclaringClassName();
        String sourceFilename = typeValidationData.getSourceFilename();
        if (declaringClassName == null || sourceFilename == null) {
            return problem;
        }

        String cacheKey = declaringClassName + "#" + sourceFilename;
        Optional<File> maybeSourceFile = resolvedFileCache.computeIfAbsent(cacheKey, k ->
            Optional.ofNullable(resolveSourceFile(sourceRoots, declaringClassName, sourceFilename))
        );
        if (!maybeSourceFile.isPresent()) {
            return problem;
        }

        File sourceFile = maybeSourceFile.get();
        Integer lineNumber = typeValidationData.getLineNumber();
        ProblemBuilderInternal builder = problemInternal.toBuilder(problemsService.getInfrastructure());
        if (lineNumber != null) {
            builder.lineInFileLocation(sourceFile.getAbsolutePath(), lineNumber);
        } else {
            builder.fileLocation(sourceFile.getAbsolutePath());
        }
        return builder.build();
    }

    @Nullable
    static File resolveSourceFile(List<File> sourceRoots, String declaringClassName, String sourceFilename) {
        // Step 1: Package-based lookup
        String packagePath = "";
        int lastDot = declaringClassName.lastIndexOf('.');
        if (lastDot > 0) {
            packagePath = declaringClassName.substring(0, lastDot).replace('.', File.separatorChar);
        }
        String relativePath = packagePath.isEmpty() ? sourceFilename : packagePath + File.separator + sourceFilename;

        Set<String> matchedCanonicalPaths = new HashSet<>();
        List<File> packageMatchedFiles = new ArrayList<>();

        for (File root : sourceRoots) {
            File candidate = new File(root, relativePath);
            if (candidate.isFile()) {
                try {
                    String canonical = candidate.getCanonicalPath();
                    if (matchedCanonicalPaths.add(canonical)) {
                        packageMatchedFiles.add(candidate);
                    }
                } catch (IOException e) {
                    if (matchedCanonicalPaths.add(candidate.getAbsolutePath())) {
                        packageMatchedFiles.add(candidate);
                    }
                }
            }
        }

        if (packageMatchedFiles.size() == 1) {
            return packageMatchedFiles.get(0);
        }
        if (packageMatchedFiles.size() > 1) {
            // Ambiguous across different source roots
            return null;
        }

        // Step 2: Fallback for layouts where file is not in package directory (e.g. Kotlin non-package layout)
        Set<String> fallbackCanonicalPaths = new HashSet<>();
        List<File> fallbackMatchedFiles = new ArrayList<>();

        for (File root : sourceRoots) {
            findFilesByName(root, sourceFilename, fallbackCanonicalPaths, fallbackMatchedFiles);
            if (fallbackMatchedFiles.size() > 1) {
                // Ambiguous
                return null;
            }
        }

        if (fallbackMatchedFiles.size() == 1) {
            return fallbackMatchedFiles.get(0);
        }

        return null;
    }

    private static void findFilesByName(File dir, String filename, Set<String> matchedPaths, List<File> matchedFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                findFilesByName(file, filename, matchedPaths, matchedFiles);
                if (matchedFiles.size() > 1) {
                    return;
                }
            } else if (file.isFile() && file.getName().equals(filename)) {
                try {
                    String canonical = file.getCanonicalPath();
                    if (matchedPaths.add(canonical)) {
                        matchedFiles.add(file);
                    }
                } catch (IOException e) {
                    if (matchedPaths.add(file.getAbsolutePath())) {
                        matchedFiles.add(file);
                    }
                }
            }
        }
    }

    private static void storeResults(List<Problem> warnings, List<Problem> errors, RegularFileProperty outputFile, ProblemGroupsInternal groups) {
        if (outputFile.isPresent()) {
            File output = outputFile.get().getAsFile();
            try {
                //noinspection ResultOfMethodCallIgnored
                output.createNewFile();
                Files.asCharSink(output, StandardCharsets.UTF_8).write(ValidationProblemSerialization.serialize(warnings, errors, groups));
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

    private static class ValidationProblemCollector extends EmptyFileVisitor {
        private final ClassLoader classLoader;
        private final List<Problem> taskValidationWarnings;
        private final List<Problem> taskValidationErrors;
        private final Params params;
        private final ProblemsInternal problems;

        public ValidationProblemCollector(List<Problem> taskValidationWarnings, List<Problem> taskValidationErrors, Params params, ProblemsInternal problems) {
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.taskValidationErrors = taskValidationErrors;
            this.taskValidationWarnings = taskValidationWarnings;
            this.params = params;
            this.problems = problems;
        }

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
                collectValidationProblems(clazz, taskValidationWarnings, taskValidationErrors, params.getEnableStricterValidation().get(), problems);
            }
        }

        private static void collectValidationProblems(Class<?> topLevelBean, List<Problem> taskValidationWarnings, List<Problem> taskValidationErrors, boolean enableStricterValidation, ProblemsInternal problems) {
            DefaultTypeValidationContext validationContext = createTypeValidationContext(topLevelBean, enableStricterValidation, problems);
            PropertyValidationAccess.collectValidationProblems(topLevelBean, validationContext);

            taskValidationWarnings.addAll(validationContext.getWarnings());
            taskValidationErrors.addAll(validationContext.getErrors());
        }

        private static DefaultTypeValidationContext createTypeValidationContext(Class<?> topLevelBean, boolean enableStricterValidation, ProblemsInternal problems) {
            if (Task.class.isAssignableFrom(topLevelBean)) {
                return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTask.class, enableStricterValidation, problems);
            }
            if (TransformAction.class.isAssignableFrom(topLevelBean)) {
                return createValidationContextAndValidateCacheableAnnotations(topLevelBean, CacheableTransform.class, enableStricterValidation, problems);
            }
            return createValidationContext(topLevelBean, enableStricterValidation, problems);
        }

        private static DefaultTypeValidationContext createValidationContextAndValidateCacheableAnnotations(Class<?> topLevelBean, Class<? extends Annotation> cacheableAnnotationClass, boolean enableStricterValidation, ProblemsInternal problems) {
            boolean cacheable = topLevelBean.isAnnotationPresent(cacheableAnnotationClass);
            DefaultTypeValidationContext validationContext = createValidationContext(topLevelBean, cacheable || enableStricterValidation, problems);
            if (enableStricterValidation) {
                validateCacheabilityAnnotationPresent(topLevelBean, cacheable, cacheableAnnotationClass, validationContext);
            }
            return validationContext;
        }

        private static DefaultTypeValidationContext createValidationContext(Class<?> topLevelBean, boolean reportCacheabilityProblems, ProblemsInternal problems) {
            return DefaultTypeValidationContext.withRootType(topLevelBean, reportCacheabilityProblems, problems);
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
                validationContext.visitTypeError(problem -> {
                        ProblemSpec builder = problem
                            .withAnnotationType(topLevelBean)
                            .id(problem.getGradleGroup().getPluginValidation().problemId("Not cacheable without reason"))
                            .contextualLabel("must be annotated either with " + cacheableAnnotation + " or with " + disableCachingAnnotation)
                            .documentedAt(userManual("validation_problems", "disable_caching_by_default"))
                            .details("The " + workType + " author should make clear why a " + workType + " is not cacheable")
                            .solution("Add " + disableCachingAnnotation + "(because = ...)")
                            .solution("Add " + cacheableAnnotation);
                        if (isTask) {
                            builder.solution("Add " + untrackedTaskAnnotation + "(because = ...)");
                        }
                    }
                );
            }
        }

        private static List<String> getClassNames(FileVisitDetails fileDetails) {
            ClassReader reader = createClassReader(fileDetails);
            List<String> classNames = new ArrayList<>();
            reader.accept(new TaskNameCollectorVisitor(classNames), ClassReader.SKIP_CODE);
            return classNames;
        }

        private static ClassReader createClassReader(FileVisitDetails fileDetails) {
            try {
                return new ClassReader(Files.asByteSource(fileDetails.getFile()).read());
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
