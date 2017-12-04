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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.DefaultPropertySpecFactory
import org.gradle.api.internal.tasks.InputsOutputVisitor
import org.gradle.api.internal.tasks.PropertyInfo
import org.gradle.api.internal.tasks.PropertySpecFactory
import org.gradle.api.internal.tasks.TaskPropertiesWalker
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Shared

import java.lang.annotation.Annotation

class DefaultTaskClassValidatorExtractorTest extends AbstractProjectBuilderSpec {
    private static final List<Class<? extends Annotation>> PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        InputFile, InputFiles, InputDirectory, OutputFile, OutputDirectory, OutputFiles, OutputDirectories
    ]

    private static final List<Class<? extends Annotation>> UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Console, Internal
    ]

    @Shared GroovyClassLoader groovyClassLoader

    def setupSpec() {
        groovyClassLoader = new GroovyClassLoader(getClass().classLoader)
    }

    static  class TaskWithCustomAnnotation extends DefaultTask {
        @SearchPath FileCollection searchPath;
    }

    class SearchPathAnnotationHandler implements PropertyAnnotationHandler {
        private final visitorAction

        SearchPathAnnotationHandler(visitorAction) {
            this.visitorAction = visitorAction
        }

        @Override
        Class<? extends Annotation> getAnnotationType() {
            SearchPath
        }


        @Override
        void accept(PropertyInfo propertyInfo, InputsOutputVisitor visitor, PropertySpecFactory specFactory) {
            visitorAction.call(propertyInfo, visitor)
        }
    }

    def "can use custom annotation processor"() {
        def annotatedProperties = []
        def configureAction = { propertyInfo, visitor ->
            annotatedProperties << propertyInfo.propertyName
        }
        def annotationHandler = new SearchPathAnnotationHandler(configureAction)
        def walker = new TaskPropertiesWalker([annotationHandler])
        def visitor = Mock(InputsOutputVisitor)

        when:
        visitTask(walker, TaskWithCustomAnnotation, visitor)

        then:
        annotatedProperties == ["searchPath"]
    }

    private void visitTask(TaskPropertiesWalker walker = new TaskPropertiesWalker([]), Class taskClass, InputsOutputVisitor visitor) {
        def task = project.tasks.create(taskClass.simpleName, taskClass)
        def specFactory = new DefaultPropertySpecFactory(task, TestFiles.resolver())
        walker.visitInputs(specFactory, visitor, task)
    }

    static class TaskWithInputFile extends DefaultTask {
        @InputFile getFile() {}
    }

    static class TaskWithInternal extends TaskWithInputFile {
        @Internal @Override getFile() {}
    }

    static class TaskWithOutputFile extends TaskWithInternal {
        @OutputFile @Override getFile() {}
    }

    def "can make property internal and then make it into another type of property"() {
        def visitor = Mock(InputsOutputVisitor)

        when:
        visitTask(TaskWithInputFile, visitor)

        then:
        1 * visitor.visitInputFileProperty(_)
        0 * _

        when:
        visitTask(TaskWithInternal, visitor)

        then:
        0 * _

        when:
        visitTask(TaskWithOutputFile, visitor)

        then:
        1 * visitor.visitOutputFileProperty(_)
    }

//    @Unroll
//    def "can override @#parentAnnotation.simpleName property type with @#childAnnotation.simpleName"() {
//        def parentTask = groovyClassLoader.parseClass """
//            class ParentTask extends org.gradle.api.DefaultTask {
//                @$parentAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def childTask = groovyClassLoader.parseClass """
//            class ChildTask extends ParentTask {
//                @Override @$childAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        def parentValidator = extractor.extractValidator(parentTask)
//        def childValidator = extractor.extractValidator(childTask)
//
//        expect:
//        parentValidator.annotatedProperties[0].propertyType == parentAnnotation
//        childValidator.annotatedProperties[0].propertyType == childAnnotation
//        parentValidator.validationMessages.empty
//        childValidator.validationMessages.empty
//
//        where:
//        [parentAnnotation, childAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, PROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
//    }
//
//    @Unroll
//    def "can override @#processedAnnotation.simpleName property type with @#unprocessedAnnotation.simpleName"() {
//        def parentTask = groovyClassLoader.parseClass """
//            class ParentTask extends org.gradle.api.DefaultTask {
//                @$processedAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def childTask = groovyClassLoader.parseClass """
//            class ChildTask extends ParentTask {
//                @Override @$unprocessedAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        def parentValidator = extractor.extractValidator(parentTask)
//        def childValidator = extractor.extractValidator(childTask)
//
//        expect:
//        parentValidator.annotatedProperties[0].propertyType == processedAnnotation
//        childValidator.annotatedProperties.empty
//        parentValidator.validationMessages.empty
//        childValidator.validationMessages.empty
//
//        where:
//        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
//    }
//
//    @Unroll
//    def "can override @#unprocessedAnnotation.simpleName property type with @#processedAnnotation.simpleName"() {
//        def parentTask = groovyClassLoader.parseClass """
//            class ParentTask extends org.gradle.api.DefaultTask {
//                @$unprocessedAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def childTask = groovyClassLoader.parseClass """
//            class ChildTask extends ParentTask {
//                @Override @$processedAnnotation.name Object getValue() { null }
//            }
//        """
//
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        def parentValidator = extractor.extractValidator(parentTask)
//        def childValidator = extractor.extractValidator(childTask)
//        expect:
//        parentValidator.annotatedProperties.empty
//        childValidator.annotatedProperties[0].propertyType == processedAnnotation
//        parentValidator.validationMessages.empty
//        childValidator.validationMessages.empty
//
//        where:
//        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
//    }
//
//    class ClasspathPropertyTask extends DefaultTask {
//        @Classpath @InputFiles FileCollection inputFiles1
//        @InputFiles @Classpath FileCollection inputFiles2
//    }
//
//    // Third-party plugins that need to support Gradle versions both pre- and post-3.2
//    // need to declare their @Classpath properties as @InputFiles as well
//    @Issue("https://github.com/gradle/gradle/issues/913")
//    def "@Classpath takes precedence over @InputFiles when both are declared on property"() {
//        def extractor = new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler())
//
//        when:
//        def validator = extractor.extractValidator(ClasspathPropertyTask)
//
//        then:
//        validator.annotatedProperties*.name as List == ["inputFiles1", "inputFiles2"]
//        validator.annotatedProperties*.propertyType as List == [Classpath, Classpath]
//        validator.validationMessages.empty
//    }
//
//    class BaseClasspathPropertyTask extends DefaultTask {
//        @Classpath FileCollection overriddenClasspath
//        @InputFiles FileCollection overriddenInputFiles
//    }
//
//    class OverridingClasspathPropertyTask extends BaseClasspathPropertyTask {
//        @InputFiles
//        @Override
//        FileCollection getOverriddenClasspath() {
//            return super.getOverriddenClasspath()
//        }
//
//        @Classpath
//        @Override
//        FileCollection getOverriddenInputFiles() {
//            return super.getOverriddenInputFiles()
//        }
//    }
//
//    @Issue("https://github.com/gradle/gradle/issues/913")
//    def "@Classpath does not take precedence over @InputFiles when overriding properties in child type"() {
//        def extractor = new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler())
//
//        when:
//        def validator = extractor.extractValidator(OverridingClasspathPropertyTask)
//
//        then:
//        validator.annotatedProperties*.name as List == ["overriddenClasspath", "overriddenInputFiles"]
//        validator.annotatedProperties*.propertyType as List == [InputFiles, Classpath]
//        validator.validationMessages.empty
//    }
//
//    class TaskWithNonAnnotatedProperty extends DefaultTask {
//        FileCollection inputFiles
//    }
//
//    def "warns about non-annotated property"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithNonAnnotatedProperty)
//
//        then:
//        validator.validationMessages*.toString() == [
//                "property 'inputFiles' is not annotated with an input or output annotation"
//        ]
//    }
//
//    class TaskWithBothFieldAndGetterAnnotation extends DefaultTask {
//        @InputFiles FileCollection inputFiles
//
//        @InputFiles
//        FileCollection getInputFiles() {
//            return inputFiles
//        }
//    }
//
//    def "warns about both method and field having the same annotation"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithBothFieldAndGetterAnnotation)
//
//        then:
//        validator.validationMessages*.toString() == [
//                "property 'inputFiles' has both a getter and field declared with annotation @InputFiles"
//        ]
//    }
//
//    class TaskWithBothFieldAndGetterAnnotationButIrrelevant extends DefaultTask {
//        @Nullable FileCollection inputFiles
//
//        @Nullable @InputFiles
//        FileCollection getInputFiles() {
//            return inputFiles
//        }
//    }
//
//    def "doesn't warn about both method and field having the same irrelevant annotation"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithBothFieldAndGetterAnnotationButIrrelevant)
//
//        then:
//        validator.validationMessages.empty
//    }
//
//    class TaskWithAnnotationsOnPrivateProperties extends DefaultTask {
//        @Input
//        private String getInput() {
//            'Input'
//        }
//
//        @OutputFile
//        private File getOutputFile() {
//            null
//        }
//
//        private String getNotAnInput() {
//            'Not an input'
//        }
//    }
//
//    def "warns about annotations on private properties"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithAnnotationsOnPrivateProperties)
//
//        then:
//        validator.validationMessages*.toString() == [
//            "property 'input' is private and annotated with an input or output annotation",
//            "property 'outputFile' is private and annotated with an input or output annotation",
//        ]
//    }
//
//    class TaskWithConflictingPropertyTypes extends DefaultTask {
//        @InputFile
//        @InputDirectory
//        File inputThing
//
//        @InputFile
//        @OutputFile
//        File confusedFile
//    }
//
//    def "warns about conflicting property types being specified"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithConflictingPropertyTypes)
//
//        then:
//        validator.validationMessages*.toString() == [
//            "property 'confusedFile' has conflicting property types declared: @InputFile, @OutputFile",
//            "property 'inputThing' has conflicting property types declared: @InputFile, @InputDirectory"
//        ]
//    }
//
//    class TaskWithNonConflictingPropertyTypes extends DefaultTask {
//        @InputFiles
//        @Classpath
//        FileCollection classpath
//    }
//
//    def "doesn't warn about non-conflicting property types being specified"() {
//        def extractor = new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler())
//
//        when:
//        def validator = extractor.extractValidator(TaskWithNonConflictingPropertyTypes)
//
//        then:
//        validator.validationMessages.empty
//    }
//
//    class TaskWithFileInput extends DefaultTask {
//        @Input
//        File file
//
//        @Input
//        Path filePath
//
//        @Input
//        FileCollection fileCollection
//
//        @Input
//        FileTree fileTree
//    }
//
//    def "warns about @Input being used on File and FileCollection properties"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(TaskWithFileInput)
//
//        then:
//        validator.validationMessages*.toString() == [
//            "property 'file' has @Input annotation used on property of type $File.name",
//            "property 'fileCollection' has @Input annotation used on property of type $FileCollection.name",
//            "property 'filePath' has @Input annotation used on property of type $Path.name",
//            "property 'fileTree' has @Input annotation used on property of type $FileTree.name"
//        ]
//    }
//
//    @CacheableTask
//    class CacheableTaskWithoutPathSensitivity extends DefaultTask {
//        @InputFile
//        File inputFile
//
//        @InputFiles
//        FileCollection inputFiles
//
//        @OutputFile
//        File outputFile
//    }
//
//    def "warns about missing @PathSensitive annotation for @CacheableTask"() {
//        def extractor = new DefaultTaskClassValidatorExtractor()
//
//        when:
//        def validator = extractor.extractValidator(CacheableTaskWithoutPathSensitivity)
//
//        then:
//        validator.validationMessages*.toString() == [
//            "property 'inputFile' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE",
//            "property 'inputFiles' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE"
//        ]
//    }
}
