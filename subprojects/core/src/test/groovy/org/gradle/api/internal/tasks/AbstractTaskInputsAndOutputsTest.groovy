/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.properties.GetInputPropertiesVisitor
import org.gradle.api.internal.tasks.properties.InputParameterUtils
import org.gradle.api.internal.tasks.properties.bean.TestImplementationResolver
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.properties.annotations.DefaultTypeMetadataStore
import org.gradle.internal.properties.annotations.NoOpPropertyAnnotationHandler
import org.gradle.internal.properties.annotations.TestPropertyTypeResolver
import org.gradle.internal.properties.bean.DefaultPropertyWalker
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile

import javax.annotation.Nullable

abstract class AbstractTaskInputsAndOutputsTest extends AbstractProjectBuilderSpec {

    final fileCollectionFactory = TestFiles.fileCollectionFactory(temporaryFolder.testDirectory)

    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        [:],
        ["java", "groovy"],
        [],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        [Internal],
        { false },
        cacheFactory
    )
    def propertyHandlers = [new NoOpPropertyAnnotationHandler(Internal)]
    def typeMetadataStore = new DefaultTypeMetadataStore([], propertyHandlers, [], typeAnnotationMetadataStore, TestPropertyTypeResolver.INSTANCE, cacheFactory)
    def walker = new DefaultPropertyWalker(typeMetadataStore, new TestImplementationResolver(), propertyHandlers)

    TaskInternal task
    TaskInputsInternal inputs
    TaskOutputsInternal outputs

    @SuppressWarnings('ConfigurationAvoidance')
    def setup() {
        task = project.tasks.create("test") as TaskInternal
        inputs = task.inputs
        outputs = task.outputs
    }

    TestFile file(Object path) {
        new TestFile(project.projectDir, path)
    }

    Set<File> files(Object... paths) {
        paths.collect { file(it) } as Set
    }

    def inputProperties() {
        def visitor = new GetInputPropertiesVisitor()
        TaskPropertyUtils.visitProperties(walker, task, visitor)
        return visitor.properties.collectEntries { [it.propertyName, InputParameterUtils.prepareInputParameterValue(it.value)] }
    }

    def inputFileProperties() {
        def inputFiles = [:]
        TaskPropertyUtils.visitProperties(walker, task, new PropertyVisitor() {
            @Override
            void visitInputFileProperty(
                String propertyName,
                boolean optional,
                InputBehavior behavior,
                DirectorySensitivity emptyDirectorySensitivity,
                LineEndingSensitivity lineEndingNormalization,
                @Nullable FileNormalizer fileNormalizer,
                PropertyValue value,
                InputFilePropertyType filePropertyType
            ) {
                inputFiles[propertyName] = value.call()
            }
        })
        return inputFiles
    }
}
