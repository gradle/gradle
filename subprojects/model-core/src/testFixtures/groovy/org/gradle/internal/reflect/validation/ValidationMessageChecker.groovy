/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.reflect.validation

import groovy.transform.CompileStatic
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.reflect.JavaReflectionUtil

@CompileStatic
trait ValidationMessageChecker {
    private final DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    String userguideLink(String id, String section) {
        documentationRegistry.getDocumentationFor(id, section)
    }

    String learnAt(String id, String section) {
        "Please refer to ${userguideLink(id, section)} for more details about this problem"
    }

    String missingValueMessage(@DelegatesTo(value = SimpleMessage, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(MethodShouldNotBeAnnotated, 'value_not_set', spec)
        config.render "doesn't have a configured value. This property isn't marked as optional and no value has been configured. Possible solutions: Assign a value to '${config.property}' or mark property '${config.property}' as optional."
    }

    String methodShouldNotBeAnnotatedMessage(@DelegatesTo(value = MethodShouldNotBeAnnotated, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(MethodShouldNotBeAnnotated, 'ignored_annotations_on_method', spec)
        String message = "$config.kind '$config.method()' should not be annotated with: @$config.annotation. Input/Output annotations are ignored if they are placed on something else than a getter. Possible solutions: Remove the annotations or rename the method."
        config.render message
    }

    String privateGetterAnnotatedMessage(@DelegatesTo(value = AnnotationContext, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(AnnotationContext, 'private_getter_must_not_be_annotated', spec)
        config.render "is private and annotated with @${config.annotation}. Annotations on private getters are ignored. Possible solutions: Make the getter public or annotate the public version of the getter."
    }

    String ignoredAnnotatedPropertyMessage(@DelegatesTo(value = IgnoredAnnotationPropertyMessage, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(IgnoredAnnotationPropertyMessage, 'ignored_property_must_not_be_annotated', spec)
        config.render "annotated with @${config.ignoringAnnotation} should not be also annotated with @${config.alsoAnnotatedWith}. A property is ignored but also has input annotations. Possible solutions: Remove the input annotations or remove the @${config.ignoringAnnotation} annotation."
    }

    String conflictingAnnotationsMessage(@DelegatesTo(value = ConflictingAnnotation, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(ConflictingAnnotation, 'conflicting_annotations', spec)
        String annotations = config.inConflict.collect { "@$it" }.join(", ")
        config.render "has conflicting $config.kind: $annotations. The different annotations have different semantics and Gradle cannot determine which one to pick. Possible solution: Choose between one of the conflicting annotations."
    }

    String annotationInvalidInContext(@DelegatesTo(value = AnnotationContext, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(AnnotationContext, 'annotation_invalid_in_context', spec)
        config.render "is annotated with invalid property type @${config.annotation}. The '@${config.annotation}' annotation cannot be used in this context. Possible solutions: Remove the property or use a different annotation, e.g one of ${config.validAnnotations}."
    }

    String missingAnnotationMessage(@DelegatesTo(value=MissingAnnotation, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(MissingAnnotation, 'missing_annotation', spec)
        config.render "is missing ${config.kind}. A property without annotation isn't considered during up-to-date checking. Possible solutions: Add ${config.kind} or mark it as @Internal."
    }

    String incompatibleAnnotations(@DelegatesTo(value=IncompatibleAnnotations, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(IncompatibleAnnotations, 'incompatible_annotations', spec)
        config.render "is annotated with @${config.annotatedWith} but that is not allowed for '${config.incompatibleWith}' properties. This modifier is used in conjunction with a property of type '${config.incompatibleWith}' but this doesn't have semantics. Possible solution: Remove the '@${config.annotatedWith}' annotation."
    }

    String incorrectUseOfInputAnnotation(@DelegatesTo(value=IncorrectUseOfInputAnnotation, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(IncorrectUseOfInputAnnotation, 'incorrect_use_of_input_annotation', spec)
        config.render "has @Input annotation used on property of type '${config.propertyType}'. A property of type '${config.propertyType}' annotated with @Input cannot determine how to interpret the file. Possible solutions: Annotate with @InputFile for regular files or annotate with @InputDirectory for directories or if you want to track the path, return File.absolutePath as a String and keep @Input."
    }

    String missingNormalizationStrategy(@DelegatesTo(value=MissingNormalization, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(MissingNormalization, 'missing_normalization_annotation', spec)
        config.render "is annotated with @${config.annotatedWith} but missing a normalization strategy. If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly. Possible solution: Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath."
    }

    String unresolvableInput(@DelegatesTo(value=SimpleMessage, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(SimpleMessage, 'unresolvable_input', spec)
        config.render "An input file collection couldn't be resolved, making it impossible to determine task inputs. Possible solution: Consider using Task.dependsOn instead."
    }

    String implicitDependency(@DelegatesTo(value=ImplicitDependency, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(ImplicitDependency, 'implicit_dependency', spec)
        config.render "Gradle detected a problem with the following location: '${config.location.absolutePath}'. Task '${config.consumer}' uses this output of task '${config.producer}' without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed. Possible solutions: Declare task '${config.producer}' as an input of '${config.consumer}' or declare an explicit dependency on '${config.producer}' from '${config.consumer}' using Task#dependsOn or declare an explicit dependency on '${config.producer}' from '${config.consumer}' using Task#mustRunAfter."
    }

    String inputDoesNotExist(@DelegatesTo(value=IncorrectInputMessage, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(IncorrectInputMessage, 'input_file_does_not_exist', spec)
        config.render "specifies ${config.kind} '${config.file}' which doesn't exist. An input file was expected to be present but it doesn't exist. Possible solutions: Make sure the ${config.kind} exists before the task is called or make sure that the task which produces the ${config.kind} is declared as an input."
    }

    String unexpectedInputType(@DelegatesTo(value=IncorrectInputMessage, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(IncorrectInputMessage, 'unexpected_input_file_type', spec)
        config.render "${config.kind} '${config.file}' is not a ${config.kind}. Expected an input to be a ${config.kind} but it was a ${config.oppositeKind}. Possible solutions: Use a ${config.kind} as an input or declare the input as a ${config.oppositeKind} instead."
    }

    String cannotWriteToDir(@DelegatesTo(value=CannotWriteToDir, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(CannotWriteToDir, 'cannot_write_output', spec)
        config.render "is not writable because '${config.dir}' ${config.reason}. Expected '${config.problemDir}' to be a directory but it's a file. Possible solution: Make sure that the '${config.property}' is configured to a directory."
    }

    String cannotWriteToFile(@DelegatesTo(value=CannotWriteToFile, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(CannotWriteToFile, 'cannot_write_output', spec)
        config.render "is not writable because '${config.file}' ${config.reason}. Cannot write a file to a location pointing at a directory. Possible solution: Configure '${config.property}' to point to a file, not a directory."
    }

    String cannotWriteToReservedLocation(@DelegatesTo(value=ForbiddenPath, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(ForbiddenPath, 'cannot_write_to_reserved_location', spec)
        config.render "points to '${config.location}' which is managed by Gradle. Trying to write an output to a read-only location which is for Gradle internal use only. Possible solution: Select a different output location."
    }

    String unsupportedNotation(@DelegatesTo(value=UnsupportedNotation, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(UnsupportedNotation, 'unsupported_notation', spec)
        config.render "has unsupported value '${config.value}'. Type '${config.type}' cannot be converted to a ${config.targetType}. ${config.solution}"
    }

    String invalidUseOfCacheableAnnotation(@DelegatesTo(value=InvalidUseOfCacheable, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(InvalidUseOfCacheable, 'invalid_use_of_cacheable_annotation', spec)
        config.render "Using @${config.invalidAnnotation} here is incorrect. This annotation only makes sense on ${config.correctType} types. Possible solution: Remove the annotation"
    }

    String optionalOnPrimitive(@DelegatesTo(value=OptionalOnPrimitive, strategy=Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def config = display(OptionalOnPrimitive, 'cannot_use_optional_on_primitive_types', spec)
        config.render "of type ${config.primitiveType.name} shouldn't be annotated with @Optional. Properties of primitive type cannot be optional. Possible solutions: Remove the @Optional annotation or use the ${config.wrapperType.name} type instead"
    }

    void expectThatExecutionOptimizationDisabledWarningIsDisplayed(GradleExecuter executer, String message, String docId = 'more_about_tasks', String section = 'sec:up_to_date_checks') {
        executer.expectDocumentedDeprecationWarning("$message " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 8.0. " +
            "Execution optimizations are disabled to ensure correctness. " +
            "See https://docs.gradle.org/current/userguide/${docId}.html#${section} for more details.")
    }

    private <T extends ValidationMessageDisplayConfiguration> T display(Class<T> clazz, String docSection, @DelegatesTo(value = ValidationMessageDisplayConfiguration, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def conf = clazz.newInstance(this)
        conf.section = docSection
        spec.delegate = conf
        spec()
        return (T) conf
    }

    static class OptionalOnPrimitive extends ValidationMessageDisplayConfiguration<OptionalOnPrimitive> {
        Class<?> primitiveType

        OptionalOnPrimitive(ValidationMessageChecker checker) {
            super(checker)
        }

        OptionalOnPrimitive primitive(Class<?> primitiveType) {
            this.primitiveType = primitiveType
            this
        }

        Class<?> getWrapperType() {
            JavaReflectionUtil.getWrapperTypeForPrimitiveType(primitiveType)
        }
    }

    static class InvalidUseOfCacheable extends ValidationMessageDisplayConfiguration<InvalidUseOfCacheable> {
        String invalidAnnotation
        String correctType

        InvalidUseOfCacheable(ValidationMessageChecker checker) {
            super(checker)
        }

        InvalidUseOfCacheable invalidAnnotation(String type) {
            this.invalidAnnotation = type
            this
        }

        InvalidUseOfCacheable onlyMakesSenseOn(String type) {
            this.correctType = type
            this
        }
    }

    static class UnsupportedNotation extends ValidationMessageDisplayConfiguration<UnsupportedNotation> {
        String type
        String value
        String targetType
        List<String> candidates = []

        UnsupportedNotation(ValidationMessageChecker checker) {
            super(checker)
        }

        UnsupportedNotation value(String value, String type = 'DefaultTask') {
            this.value = value
            this.type = type
            this
        }

        UnsupportedNotation cannotBeConvertedTo(String type) {
            this.targetType = type
            this
        }

        UnsupportedNotation candidates(String... candidates) {
            Collections.addAll(this.candidates, candidates)
            this
        }

        String getSolution() {
            def solutions = candidates.collect { "use $it" }.join(" or ").capitalize()
            "Possible solution${candidates.size()>1 ? 's':''}: $solutions"
        }
    }

    static class ForbiddenPath extends ValidationMessageDisplayConfiguration<ForbiddenPath> {
        File location

        ForbiddenPath(ValidationMessageChecker checker) {
            super(checker)
        }

        ForbiddenPath forbiddenAt(File location) {
            this.location = location
            this
        }
    }

    static class CannotWriteToDir extends ValidationMessageDisplayConfiguration<CannotWriteToDir> {
        File dir
        File problemDir
        String reason

        CannotWriteToDir(ValidationMessageChecker checker) {
            super(checker)
        }

        CannotWriteToDir dir(File directory) {
            this.problemDir = directory
            this.dir = directory
            this
        }

        CannotWriteToDir isNotDirectory() {
            this.reason = "is not a directory"
            this
        }

        CannotWriteToDir ancestorIsNotDirectory(File ancestor) {
            this.problemDir = ancestor
            this.reason = "ancestor '$ancestor' is not a directory"
            this
        }
    }

    static class CannotWriteToFile extends ValidationMessageDisplayConfiguration<CannotWriteToFile> {
        File file
        File problemDir
        String reason

        CannotWriteToFile(ValidationMessageChecker checker) {
            super(checker)
        }

        CannotWriteToFile file(File directory) {
            this.problemDir = directory
            this.file = directory
            this
        }

        CannotWriteToFile isNotFile() {
            this.reason = "is not a file"
            this
        }

        CannotWriteToFile ancestorIsNotDirectory(File ancestor) {
            this.problemDir = ancestor
            this.reason = "ancestor '$ancestor' is not a directory"
            this
        }
    }

    static class IncorrectInputMessage extends ValidationMessageDisplayConfiguration<IncorrectInputMessage> {
        String kind
        File file

        IncorrectInputMessage(ValidationMessageChecker checker) {
            super(checker)
        }

        IncorrectInputMessage file(File target) {
            kind('file')
            file = target
            this
        }

        IncorrectInputMessage dir(File target) {
            kind('directory')
            file = target
            this
        }

        IncorrectInputMessage kind(String kind) {
            this.kind = kind.toLowerCase()
            this
        }

        IncorrectInputMessage missing(File file) {
            this.file = file
            this
        }

        IncorrectInputMessage unexpected(File file) {
            this.file = file
            this
        }

        String getOppositeKind() {
            switch (kind) {
                case 'file':
                    return 'directory'
                case 'directory':
                    return 'file'
            }
            return 'unexpected file type'
        }
    }

    static class ImplicitDependency extends ValidationMessageDisplayConfiguration<ImplicitDependency> {
        String producer
        String consumer
        File location

        ImplicitDependency(ValidationMessageChecker checker) {
            super(checker)
        }

        ImplicitDependency producer(String producer) {
            this.producer = producer
            this
        }

        ImplicitDependency consumer(String consumer) {
            this.consumer = consumer
            this
        }

        ImplicitDependency at(File location) {
            this.location = location
            this
        }
    }

    static class MissingNormalization extends ValidationMessageDisplayConfiguration<MissingNormalization> {
        String annotatedWith

        MissingNormalization(ValidationMessageChecker checker) {
            super(checker)
        }

        MissingNormalization annotatedWith(String name) {
            annotatedWith = name
            this
        }
    }

    static class IncorrectUseOfInputAnnotation extends ValidationMessageDisplayConfiguration<IncorrectUseOfInputAnnotation> {
        String propertyType

        IncorrectUseOfInputAnnotation(ValidationMessageChecker checker) {
            super(checker)
        }

        IncorrectUseOfInputAnnotation propertyType(String type) {
            propertyType = type
            this
        }
    }

    static class IncompatibleAnnotations extends ValidationMessageDisplayConfiguration<IncompatibleAnnotations> {
        String annotatedWith
        String incompatibleWith

        IncompatibleAnnotations(ValidationMessageChecker checker) {
            super(checker)
        }

        IncompatibleAnnotations annotatedWith(String name) {
            annotatedWith = name
            this
        }

        IncompatibleAnnotations incompatibleWith(String name) {
            incompatibleWith = name
            this
        }
    }

    static class MissingAnnotation extends ValidationMessageDisplayConfiguration<MissingAnnotation> {
        String kind

        MissingAnnotation(ValidationMessageChecker checker) {
            super(checker)
        }

        MissingAnnotation missingInputOrOutput() {
            kind("an input or output annotation")
        }

        MissingAnnotation missingInput() {
            kind("an input annotation")
        }

        MissingAnnotation kind(String kind) {
            this.kind = kind
            this
        }
    }

    static class SimpleMessage extends ValidationMessageDisplayConfiguration<SimpleMessage> {

        SimpleMessage(ValidationMessageChecker checker) {
            super(checker)
        }

    }

    static class MethodShouldNotBeAnnotated extends ValidationMessageDisplayConfiguration<MethodShouldNotBeAnnotated> {
        String annotation
        String kind
        String method

        MethodShouldNotBeAnnotated(ValidationMessageChecker checker) {
            super(checker)
        }

        MethodShouldNotBeAnnotated kind(String kind) {
            this.kind = kind
            this
        }

        MethodShouldNotBeAnnotated annotation(String name) {
            annotation = name
            this
        }

        MethodShouldNotBeAnnotated method(String name) {
            method = name
            this
        }
    }

    static class IgnoredAnnotationPropertyMessage extends ValidationMessageDisplayConfiguration<IgnoredAnnotationPropertyMessage> {
        String ignoringAnnotation
        String alsoAnnotatedWith

        IgnoredAnnotationPropertyMessage(ValidationMessageChecker checker) {
            super(checker)
        }

        IgnoredAnnotationPropertyMessage ignoring(String name) {
            ignoringAnnotation = name
            this
        }

        IgnoredAnnotationPropertyMessage alsoAnnotatedWith(String name) {
            alsoAnnotatedWith = name
            this
        }
    }

    static class AnnotationContext extends ValidationMessageDisplayConfiguration<AnnotationContext> {

        String annotation
        String validAnnotations = ""

        AnnotationContext(ValidationMessageChecker checker) {
            super(checker)
            forTransformParameters()
        }

        AnnotationContext annotation(String name) {
            annotation = name
            this
        }

        AnnotationContext forTransformAction() {
            validAnnotations = "@Inject, @InputArtifact or @InputArtifactDependencies"
            this
        }

        AnnotationContext forTransformParameters() {
            validAnnotations = "@Console, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @Nested or @ReplacedBy"
            this
        }

        AnnotationContext forTask() {
            validAnnotations = "@Console, @Destroys, @Inject, @Input, @InputDirectory, @InputFile, @InputFiles, @Internal, @LocalState, @Nested, @OptionValues, @OutputDirectories, @OutputDirectory, @OutputFile, @OutputFiles or @ReplacedBy"
            this
        }
    }

    static class ConflictingAnnotation extends ValidationMessageDisplayConfiguration<ConflictingAnnotation> {

        List<String> inConflict
        String kind = 'type annotations declared'

        ConflictingAnnotation(ValidationMessageChecker checker) {
            super(checker)
        }

        ConflictingAnnotation kind(String kind) {
            this.kind = kind
            this
        }

        ConflictingAnnotation inConflict(List<String> conflicting) {
            inConflict = conflicting
            this
        }

        ConflictingAnnotation inConflict(String... conflicting) {
            inConflict(Arrays.asList(conflicting))
        }
    }
}
