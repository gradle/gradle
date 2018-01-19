/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.taskfactory.SearchPath
import org.gradle.api.internal.tasks.PropertySpecFactory
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.CompileClasspathPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.InputDirectoryPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.InputFilePropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.InputFilesPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoriesPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.OutputDirectoryPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.OutputFilePropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.OutputFilesPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable
import javax.inject.Inject
import java.lang.annotation.Annotation

class DefaultPropertyMetadataStoreTest extends Specification {

    private static final List<Class<? extends Annotation>> PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        InputFile, InputFiles, InputDirectory, OutputFile, OutputDirectory, OutputFiles, OutputDirectories
    ]

    private static final List<Class<? extends Annotation>> UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Console, Internal
    ]

    private static final Map<Class<? extends Annotation>, Class<? extends PropertyValueVisitor>> VISITORS_FOR_ANNOTATION = [
        (InputFile): InputFilePropertyAnnotationHandler,
        (InputFiles): InputFilesPropertyAnnotationHandler,
        (InputDirectory): InputDirectoryPropertyAnnotationHandler,
        (OutputFile): OutputFilePropertyAnnotationHandler,
        (OutputDirectory): OutputDirectoryPropertyAnnotationHandler,
        (OutputFiles): OutputFilesPropertyAnnotationHandler,
        (OutputDirectories): OutputDirectoriesPropertyAnnotationHandler,
        (Classpath): ClasspathPropertyAnnotationHandler,
        (CompileClasspath): CompileClasspathPropertyAnnotationHandler
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
        void visitPropertyValue(PropertyValue propertyInfo, PropertyVisitor visitor, PropertySpecFactory specFactory) {
            visitorAction.call(propertyInfo, visitor)
        }
    }

    def "can use custom annotation processor"() {
        def annotatedProperties = []
        def configureAction = { propertyInfo, visitor ->
            annotatedProperties << propertyInfo.propertyName
        }
        def annotationHandler = new SearchPathAnnotationHandler(configureAction)
        def metadataStore = new DefaultPropertyMetadataStore([annotationHandler])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(TaskWithCustomAnnotation).propertiesMetadata

        then:
        typeMetadata.size() == 1
        def metadata = typeMetadata.first()
        metadata.fieldName == 'searchPath'
        metadata.propertyValueVisitor == annotationHandler
        metadata.validationMessages.empty
    }

    class TaskWithInputFile extends DefaultTask {
        @InputFile getFile() {}
    }

    class TaskWithInternal extends TaskWithInputFile {
        @Internal @Override getFile() {}
    }

    class TaskWithOutputFile extends TaskWithInternal {
        @OutputFile @Override getFile() {}
    }

    def "can make property internal and then make it into another type of property"() {
        def metadataStore = new DefaultPropertyMetadataStore([])

        expect:
        isOfType(metadataStore.getTypeMetadata(TaskWithInputFile).propertiesMetadata.first(), InputFile)
        isIgnored(metadataStore.getTypeMetadata(TaskWithInternal).propertiesMetadata.first())
        isOfType(metadataStore.getTypeMetadata(TaskWithOutputFile).propertiesMetadata.first(), OutputFile)
    }

    @Unroll
    def "can override @#parentAnnotation.simpleName property type with @#childAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$parentAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$childAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultPropertyMetadataStore([])

        def parentMetadata = metadataStore.getTypeMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypeMetadata(childTask).propertiesMetadata.first()

        expect:
        isOfType(parentMetadata, parentAnnotation)
        isOfType(childMetadata, childAnnotation)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [parentAnnotation, childAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, PROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    @Unroll
    def "can override @#processedAnnotation.simpleName property type with @#unprocessedAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$processedAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$unprocessedAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultPropertyMetadataStore([])

        def parentMetadata = metadataStore.getTypeMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypeMetadata(childTask).propertiesMetadata.first()

        expect:
        isOfType(parentMetadata, processedAnnotation)
        isIgnored(childMetadata)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    @Unroll
    def "can override @#unprocessedAnnotation.simpleName property type with @#processedAnnotation.simpleName"() {
        def parentTask = groovyClassLoader.parseClass """
            class ParentTask extends org.gradle.api.DefaultTask {
                @$unprocessedAnnotation.name Object getValue() { null }
            }
        """

        def childTask = groovyClassLoader.parseClass """
            class ChildTask extends ParentTask {
                @Override @$processedAnnotation.name Object getValue() { null }
            }
        """

        def metadataStore = new DefaultPropertyMetadataStore([])

        def parentMetadata = metadataStore.getTypeMetadata(parentTask).propertiesMetadata.first()
        def childMetadata = metadataStore.getTypeMetadata(childTask).propertiesMetadata.first()

        expect:
        isIgnored(parentMetadata)
        isOfType(childMetadata, processedAnnotation)
        parentMetadata.validationMessages.empty
        childMetadata.validationMessages.empty

        where:
        [processedAnnotation, unprocessedAnnotation] << [PROCESSED_PROPERTY_TYPE_ANNOTATIONS, UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS].combinations()*.flatten()
    }

    class ClasspathPropertyTask extends DefaultTask {
        @Classpath @InputFiles FileCollection inputFiles1
        @InputFiles @Classpath FileCollection inputFiles2
    }

    // Third-party plugins that need to support Gradle versions both pre- and post-3.2
    // need to declare their @Classpath properties as @InputFiles as well
    @Issue("https://github.com/gradle/gradle/issues/913")
    def "@Classpath takes precedence over @InputFiles when both are declared on property"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(ClasspathPropertyTask).propertiesMetadata

        then:
        typeMetadata*.fieldName as List == ["inputFiles1", "inputFiles2"]
        typeMetadata.collect { annotationForMetadata(it) } as List == [Classpath, Classpath]
        typeMetadata*.validationMessages.flatten().empty
    }

    class BaseClasspathPropertyTask extends DefaultTask {
        @Classpath FileCollection overriddenClasspath
        @InputFiles FileCollection overriddenInputFiles
    }

    class OverridingClasspathPropertyTask extends BaseClasspathPropertyTask {
        @InputFiles
        @Override
        FileCollection getOverriddenClasspath() {
            return super.getOverriddenClasspath()
        }

        @Classpath
        @Override
        FileCollection getOverriddenInputFiles() {
            return super.getOverriddenInputFiles()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/913")
    def "@Classpath does not take precedence over @InputFiles when overriding properties in child type"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(OverridingClasspathPropertyTask).propertiesMetadata

        then:
        typeMetadata*.fieldName as List == ["overriddenClasspath", "overriddenInputFiles"]
        typeMetadata.collect { annotationForMetadata(it) } as List == [InputFiles, Classpath]
        typeMetadata*.validationMessages.flatten().empty
    }

    class TaskWithBothFieldAndGetterAnnotation extends DefaultTask {
        @InputFiles FileCollection inputFiles

        @InputFiles
        FileCollection getInputFiles() {
            return inputFiles
        }
    }

    def "warns about both method and field having the same annotation"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def metadata = metadataStore.getTypeMetadata(TaskWithBothFieldAndGetterAnnotation).propertiesMetadata.first()

        then:
        metadata.validationMessages == ["has both a getter and field declared with annotation @InputFiles"]
    }

    class TaskWithBothFieldAndGetterAnnotationButIrrelevant extends DefaultTask {
        @Nullable FileCollection inputFiles

        @Nullable @InputFiles
        FileCollection getInputFiles() {
            return inputFiles
        }
    }

    def "doesn't warn about both method and field having the same irrelevant annotation"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def metadata = metadataStore.getTypeMetadata(TaskWithBothFieldAndGetterAnnotationButIrrelevant).propertiesMetadata.first()

        then:
        metadata.validationMessages.empty
    }

    class TaskWithAnnotationsOnPrivateProperties extends DefaultTask {
        @Input
        private String getInput() {
            'Input'
        }

        @OutputFile
        private File getOutputFile() {
            null
        }

        private String getNotAnInput() {
            'Not an input'
        }
    }

    def "warns about annotations on private properties"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def metadata = metadataStore.getTypeMetadata(TaskWithAnnotationsOnPrivateProperties).propertiesMetadata

        then:
        metadata*.validationMessages.flatten() as List == [
            "is private and annotated with an input or output annotation",
            "is private and annotated with an input or output annotation",
        ]
    }

    class TaskWithConflictingPropertyTypes extends DefaultTask {
        @InputFile
        @InputDirectory
        File inputThing

        @InputFile
        @OutputFile
        File confusedFile
    }

    def "warns about conflicting property types being specified"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def metadata = metadataStore.getTypeMetadata(TaskWithConflictingPropertyTypes).propertiesMetadata

        then:
        metadata*.validationMessages.flatten() as List == [
            "has conflicting property types declared: @InputFile, @OutputFile",
            "has conflicting property types declared: @InputFile, @InputDirectory"
        ]
    }

    class TaskWithNonConflictingPropertyTypes extends DefaultTask {
        @InputFiles
        @Classpath
        FileCollection classpath
    }

    def "doesn't warn about non-conflicting property types being specified"() {
        def metadataStore = new DefaultPropertyMetadataStore([new ClasspathPropertyAnnotationHandler()])

        when:
        def metadata = metadataStore.getTypeMetadata(TaskWithNonConflictingPropertyTypes).propertiesMetadata

        then:
        metadata*.validationMessages.flatten().empty
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    static class SimpleTask extends DefaultTask {
        @Input String inputString
        @InputFile File inputFile
        @InputDirectory File inputDirectory
        @InputFiles File inputFiles
        @OutputFile File outputFile
        @OutputFiles Set<File> outputFiles
        @OutputDirectory File outputDirectory
        @OutputDirectories Set<File> outputDirectories
        @Inject Object injectedService
        @Internal Object internal
        @Console boolean console
    }

    def "can get annotated properties of simple task"() {
        def metadataStore = new DefaultPropertyMetadataStore([])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(SimpleTask).propertiesMetadata

        then:
        nonIgnoredProperties(typeMetadata) == ["inputDirectory", "inputFile", "inputFiles", "inputString", "outputDirectories", "outputDirectory", "outputFile", "outputFiles"]
    }

    private static class BaseTask extends DefaultTask {
        @Input String baseValue
        @Input String superclassValue
        @Input String superclassValueWithDuplicateAnnotation
        String nonAnnotatedBaseValue
    }

    private static class OverridingTask extends BaseTask {
        @Override
        String getSuperclassValue() {
            return super.getSuperclassValue()
        }

        @Input @Override
        String getSuperclassValueWithDuplicateAnnotation() {
            return super.getSuperclassValueWithDuplicateAnnotation()
        }

        @Input @Override
        String getNonAnnotatedBaseValue() {
            return super.getNonAnnotatedBaseValue()
        }
    }

    def "overridden properties inherit super-class annotations"() {
        def metadataStore = new DefaultPropertyMetadataStore([])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(OverridingTask).propertiesMetadata

        then:
        nonIgnoredProperties(typeMetadata) == ["baseValue", "nonAnnotatedBaseValue", "superclassValue", "superclassValueWithDuplicateAnnotation"]
    }

    private interface TaskSpec {
        @Input
        String getInterfaceValue()
    }

    private static class InterfaceImplementingTask extends DefaultTask implements TaskSpec {
        @Override
        String getInterfaceValue() {
            "value"
        }
    }

    def "implemented properties inherit interface annotations"() {
        def metadataStore = new DefaultPropertyMetadataStore([])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(InterfaceImplementingTask).propertiesMetadata

        then:
        nonIgnoredProperties(typeMetadata) == ["interfaceValue"]
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class IsGetterTask extends DefaultTask {
        @Input
        private boolean feature1
        private boolean feature2

        boolean isFeature1() {
            return feature1
        }
        void setFeature1(boolean enabled) {
            this.feature1 = enabled
        }
        boolean isFeature2() {
            return feature2
        }
        void setFeature2(boolean enabled) {
            this.feature2 = enabled
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private filed is recognized for is-getter"() {
        def metadataStore = new DefaultPropertyMetadataStore([])

        when:
        def typeMetadata = metadataStore.getTypeMetadata(IsGetterTask).propertiesMetadata

        then:
        nonIgnoredProperties(typeMetadata) == ["feature1"]
    }

    private static boolean isOfType(PropertyMetadata metadata, Class<? extends Annotation> type) {
        metadata.propertyValueVisitor.class == VISITORS_FOR_ANNOTATION.get(type)
    }

    private static Class<? extends Annotation> annotationForMetadata(PropertyMetadata metadata) {
        VISITORS_FOR_ANNOTATION.find { annotation, visitorClass -> visitorClass == metadata.propertyValueVisitor.class }?.key
    }

    private static boolean isIgnored(PropertyMetadata propertyMetadata) {
        propertyMetadata.propertyValueVisitor == null || propertyMetadata.propertyValueVisitor.class == NoOpPropertyAnnotationHandler
    }

    private static List<String> nonIgnoredProperties(Set<PropertyMetadata> typeMetadata) {
        typeMetadata.findAll { !isIgnored(it) }*.fieldName.sort()
    }
}
