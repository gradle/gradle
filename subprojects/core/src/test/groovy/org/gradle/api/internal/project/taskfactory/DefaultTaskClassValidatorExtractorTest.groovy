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
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Console
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

import java.lang.annotation.Annotation

class DefaultTaskClassValidatorExtractorTest extends Specification {
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

    class TaskWithCustomAnnotation extends DefaultTask {
        @SearchPath FileCollection searchPath;
    }

    class SearchPathAnnotationHandler implements PropertyAnnotationHandler {
        private final UpdateAction configureAction

        SearchPathAnnotationHandler(UpdateAction configureAction) {
            this.configureAction = configureAction
        }

        @Override
        Class<? extends Annotation> getAnnotationType() {
            SearchPath
        }

        @Override
        void attachActions(TaskPropertyActionContext context) {
            assert context.isAnnotationPresent(SearchPath)
            context.configureAction = configureAction
        }
    }

    def "can use custom annotation processor"() {
        def configureAction = Mock(UpdateAction)
        def annotationHandler = new SearchPathAnnotationHandler(configureAction)
        def extractor = new DefaultTaskClassValidatorExtractor(annotationHandler)

        expect:
        def validator = extractor.extractValidator(TaskWithCustomAnnotation)
        validator.validatedProperties*.name as List == ["searchPath"]
        validator.validatedProperties[0].propertyType == SearchPath
        validator.validatedProperties[0].configureAction == configureAction
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
        def extractor = new DefaultTaskClassValidatorExtractor()

        expect:
        extractor.extractValidator(TaskWithInputFile).validatedProperties[0].propertyType == InputFile
        extractor.extractValidator(TaskWithInternal).validatedProperties.empty
        extractor.extractValidator(TaskWithOutputFile).validatedProperties[0].propertyType == OutputFile
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

        def extractor = new DefaultTaskClassValidatorExtractor()

        expect:
        extractor.extractValidator(parentTask).validatedProperties[0].propertyType == parentAnnotation
        extractor.extractValidator(childTask).validatedProperties[0].propertyType == childAnnotation

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

        def extractor = new DefaultTaskClassValidatorExtractor()

        expect:
        extractor.extractValidator(parentTask).validatedProperties[0].propertyType == processedAnnotation
        extractor.extractValidator(childTask).validatedProperties.empty

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

        def extractor = new DefaultTaskClassValidatorExtractor()

        expect:
        extractor.extractValidator(parentTask).validatedProperties.empty
        extractor.extractValidator(childTask).validatedProperties[0].propertyType == processedAnnotation

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
        def extractor = new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler())

        when:
        def validator = extractor.extractValidator(ClasspathPropertyTask)

        then:
        validator.validatedProperties*.name as List == ["inputFiles1", "inputFiles2"]
        validator.validatedProperties*.propertyType as List == [Classpath, Classpath]
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
        def extractor = new DefaultTaskClassValidatorExtractor(new ClasspathPropertyAnnotationHandler())

        when:
        def validator = extractor.extractValidator(OverridingClasspathPropertyTask)

        then:
        validator.validatedProperties*.name as List == ["overriddenClasspath", "overriddenInputFiles"]
        validator.validatedProperties*.propertyType as List == [InputFiles, Classpath]
    }
}
