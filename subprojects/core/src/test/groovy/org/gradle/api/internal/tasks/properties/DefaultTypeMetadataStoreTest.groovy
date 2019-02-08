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
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.HasConvention
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.tasks.properties.annotations.ClasspathPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Classpath
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
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.reflect.PropertyMetadata
import org.gradle.internal.scripts.ScriptOrigin
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import java.lang.annotation.Annotation

class DefaultTypeMetadataStoreTest extends Specification {

    private static final List<Class<? extends Annotation>> PROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        InputFile, InputFiles, InputDirectory, OutputFile, OutputDirectory, OutputFiles, OutputDirectories
    ]

    private static final List<Class<? extends Annotation>> UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = [
        Console, Internal, Inject
    ]

    @Shared GroovyClassLoader groovyClassLoader
    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def metadataStore = new DefaultTypeMetadataStore(services.getAll(PropertyAnnotationHandler), new TestCrossBuildInMemoryCacheFactory())

    def setupSpec() {
        groovyClassLoader = new GroovyClassLoader(getClass().classLoader)
    }

    static class TaskWithCustomAnnotation extends DefaultTask {
        @SearchPath FileCollection searchPath
    }

    class SearchPathAnnotationHandler implements PropertyAnnotationHandler {

        @Override
        Class<? extends Annotation> getAnnotationType() {
            SearchPath
        }

        @Override
        boolean shouldVisit(PropertyVisitor visitor) {
            return true
        }

        @Override
        void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        }
    }

    def "can use custom annotation processor"() {
        def annotationHandler = new SearchPathAnnotationHandler()
        def metadataStore = new DefaultTypeMetadataStore([annotationHandler], new TestCrossBuildInMemoryCacheFactory())

        when:
        def typeMetadata = metadataStore.getTypeMetadata(TaskWithCustomAnnotation)
        def propertiesMetadata = typeMetadata.propertiesMetadata.findAll { !isIgnored(it) }

        then:
        propertiesMetadata.size() == 1
        def propertyMetadata = propertiesMetadata.first()
        propertyMetadata.propertyName == 'searchPath'
        propertyMetadata.propertyType == SearchPath
        typeMetadata.getAnnotationHandlerFor(propertyMetadata) == annotationHandler
        collectProblems(typeMetadata).empty
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

        def parentMetadata = metadataStore.getTypeMetadata(parentTask)
        def parentProperty = parentMetadata.propertiesMetadata.first()

        def childMetadata = metadataStore.getTypeMetadata(childTask)
        def childProperty = childMetadata.propertiesMetadata.first()

        expect:
        isOfType(parentProperty, parentAnnotation)
        isOfType(childProperty, childAnnotation)
        collectProblems(parentMetadata).empty
        collectProblems(childMetadata).empty

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

        def parentMetadata = metadataStore.getTypeMetadata(parentTask)
        def parentProperty = parentMetadata.propertiesMetadata.first()

        def childMetadata = metadataStore.getTypeMetadata(childTask)
        def childProperty = childMetadata.propertiesMetadata.first()

        expect:
        isOfType(parentProperty, processedAnnotation)
        isIgnored(childProperty)
        collectProblems(parentMetadata).empty
        collectProblems(childMetadata).empty

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

        def parentMetadata = metadataStore.getTypeMetadata(parentTask)
        def parentProperty = parentMetadata.propertiesMetadata.first()

        def childMetadata = metadataStore.getTypeMetadata(childTask)
        def childProperty = childMetadata.propertiesMetadata.first()

        expect:
        isIgnored(parentProperty)
        isOfType(childProperty, processedAnnotation)
        collectProblems(parentMetadata).empty
        collectProblems(childMetadata).empty

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
        def metadataStore = new DefaultTypeMetadataStore(services.getAll(PropertyAnnotationHandler) + [new ClasspathPropertyAnnotationHandler()], new TestCrossBuildInMemoryCacheFactory())

        when:
        def typeMetadata = metadataStore.getTypeMetadata(ClasspathPropertyTask)

        then:
        def properties = typeMetadata.propertiesMetadata.findAll { !isIgnored(it) }
        properties*.propertyName as List == ["inputFiles1", "inputFiles2"]
        properties*.propertyType as List == [Classpath, Classpath]
        collectProblems(typeMetadata).empty
    }

    @Unroll
    def "all properties on #workClass are ignored"() {
        when:
        def typeMetadata = metadataStore.getTypeMetadata(workClass).propertiesMetadata.findAll { it.propertyType == null }
        then:
        typeMetadata*.propertyName.empty

        where:
        workClass << [ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class, IConventionAware.class, ExtensionAware.class, HasConvention.class, ScriptOrigin.class, DynamicObjectAware.class]
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
        when:
        def properties = metadataStore.getTypeMetadata(SimpleTask).propertiesMetadata

        then:
        nonIgnoredProperties(properties) == ["inputDirectory", "inputFile", "inputFiles", "inputString", "outputDirectories", "outputDirectory", "outputFile", "outputFiles"]
    }

    static class Unannotated extends DefaultTask {
        String bad1
        File bad2
        @Inject String ignore1
        @Input String ignore2
    }

    def "ignores properties that are not annotated"() {
        when:
        def metadata = metadataStore.getTypeMetadata(Unannotated)

        then:
        metadata.propertiesMetadata.propertyName == ["ignore1", "ignore2"]
        collectProblems(metadata) == [
            "Property 'bad1' is not annotated with an input or output annotation.",
            "Property 'bad2' is not annotated with an input or output annotation."
        ]
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

    private static List<String> collectProblems(TypeMetadata metadata) {
        def result = []
        metadata.collectValidationFailures(null, new DefaultParameterValidationContext(result))
        return result
    }

    private static boolean isOfType(PropertyMetadata metadata, Class<? extends Annotation> type) {
        metadata.propertyType == type
    }

    private static boolean isIgnored(PropertyMetadata propertyMetadata) {
        def propertyType = propertyMetadata.propertyType
        UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS.contains(propertyType)
    }

    private static List<String> nonIgnoredProperties(Collection<PropertyMetadata> typeMetadata) {
        typeMetadata.findAll { !isIgnored(it) }*.propertyName.sort()
    }
}
