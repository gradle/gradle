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

    private <T extends ValidationMessageDisplayConfiguration> T display(Class<T> clazz, String docSection, @DelegatesTo(value = ValidationMessageDisplayConfiguration, strategy = Closure.DELEGATE_FIRST) Closure<?> spec) {
        def conf = clazz.newInstance(this)
        conf.section = docSection
        spec.delegate = conf
        spec()
        return (T) conf
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
            forWorkItem()
        }

        AnnotationContext annotation(String name) {
            annotation = name
            this
        }

        AnnotationContext forInjection() {
            validAnnotations = "@Inject, @InputArtifact or @InputArtifactDependencies"
            this
        }

        AnnotationContext forWorkItem() {
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
