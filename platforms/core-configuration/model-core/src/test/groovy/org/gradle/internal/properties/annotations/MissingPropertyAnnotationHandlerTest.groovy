/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.properties.annotations

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.tasks.properties.DefaultPropertyTypeResolver
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.TypeValidationData
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.internal.reflect.validation.TypeValidationContext
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.ExecutionGlobalServices
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.lang.annotation.Annotation

class MissingPropertyAnnotationHandlerTest extends Specification {

    def services = ServiceRegistryBuilder.builder().provider(new ExecutionGlobalServices()).build()
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        ModifierAnnotationCategory.asMap([Input] as Set<Class<? extends Annotation>>),
        [:],
        ["java", "groovy"],
        [DefaultTask],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        [Console, Internal],
        [],
        { false },
        cacheFactory
    )
    def propertyTypeResolver = new DefaultPropertyTypeResolver()
    def metadataStore = new DefaultTypeMetadataStore(
        [],
        services.getAll(PropertyAnnotationHandler),
        [Classpath, CompileClasspath],
        [],
        [],
        typeAnnotationMetadataStore,
        propertyTypeResolver,
        cacheFactory,
        MissingPropertyAnnotationHandler.MISSING_INPUT_OUTPUT_HANDLER
    )

    static abstract class TypeWithUnannotatedProperty extends DefaultTask {
        String unannotatedProperty

        String getUnannotatedProperty() {
            return unannotatedProperty
        }
    }

    static abstract class SuperclassWithUnannotatedProperty extends DefaultTask {
        String inheritedUnannotatedProperty

        String getInheritedUnannotatedProperty() {
            return inheritedUnannotatedProperty
        }
    }

    static abstract class SubclassInheritingProperty extends SuperclassWithUnannotatedProperty {
        @Input
        String validProperty

        String getValidProperty() {
            return validProperty
        }
    }

    static abstract class TypeWithOptionalUnannotatedProperty extends DefaultTask {
        @Optional
        String getOptionalUnannotatedProperty() {
            return "optional"
        }
    }

    def "missing annotation problems receive source metadata"() {
        given:
        def metadata = metadataStore.getTypeMetadata(TypeWithUnannotatedProperty)
        def problems = TestUtil.problemsService()
        def validationContext = DefaultTypeValidationContext.withRootType(TypeWithUnannotatedProperty, false, problems)

        when:
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.errors.size() == 1
        ProblemInternal problem = validationContext.errors[0]
        problem.definition.id.name == "Missing annotation"

        TypeValidationData data = (TypeValidationData) problem.additionalData
        data != null
        data.propertyName == "unannotatedProperty"
        data.typeName == TypeWithUnannotatedProperty.name.replace('$', '.')
        data.declaringClassName == TypeWithUnannotatedProperty.name
        data.sourceFilename == "MissingPropertyAnnotationHandlerTest.groovy"
        data.lineNumber != null
        data.lineNumber > 0
    }

    def "inherited properties record declaring class separately from root type"() {
        given:
        def metadata = metadataStore.getTypeMetadata(SubclassInheritingProperty)
        def problems = TestUtil.problemsService()
        def validationContext = DefaultTypeValidationContext.withRootType(SubclassInheritingProperty, false, problems)

        when:
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.errors.size() == 1
        ProblemInternal problem = validationContext.errors[0]
        problem.definition.id.name == "Missing annotation"

        TypeValidationData data = (TypeValidationData) problem.additionalData
        data != null
        data.propertyName == "inheritedUnannotatedProperty"
        data.typeName == SubclassInheritingProperty.name.replace('$', '.')
        data.declaringClassName == SuperclassWithUnannotatedProperty.name
        data.sourceFilename == "MissingPropertyAnnotationHandlerTest.groovy"
        data.lineNumber != null
        data.lineNumber > 0
    }

    def "existing @Optional behavior remains unchanged while attaching source metadata"() {
        given:
        def metadata = metadataStore.getTypeMetadata(TypeWithOptionalUnannotatedProperty)
        def problems = TestUtil.problemsService()
        def validationContext = DefaultTypeValidationContext.withRootType(TypeWithOptionalUnannotatedProperty, false, problems)

        when:
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.errors.size() == 1
        ProblemInternal problem = validationContext.errors[0]
        problem.definition.id.name == "Missing annotation"
        problem.details == "@Optional is a modifier annotation and has no effect without an input or output annotation"
        problem.solutions.size() == 2
        problem.solutions[0] == "Add an input or output annotation"
        problem.solutions[1] == "Replace @Optional with @Internal for ignoring this property"

        TypeValidationData data = (TypeValidationData) problem.additionalData
        data != null
        data.propertyName == "optionalUnannotatedProperty"
        data.declaringClassName == TypeWithOptionalUnannotatedProperty.name
        data.sourceFilename == "MissingPropertyAnnotationHandlerTest.groovy"
        data.lineNumber != null
        data.lineNumber > 0
    }

    def "other missing annotation handlers like DO_NOTHING are unaffected"() {
        given:
        def doNothingStore = new DefaultTypeMetadataStore(
            [],
            services.getAll(PropertyAnnotationHandler),
            [Classpath, CompileClasspath],
            [],
            [],
            typeAnnotationMetadataStore,
            propertyTypeResolver,
            cacheFactory,
            MissingPropertyAnnotationHandler.DO_NOTHING
        )
        def metadata = doNothingStore.getTypeMetadata(TypeWithUnannotatedProperty)
        def problems = TestUtil.problemsService()
        def validationContext = DefaultTypeValidationContext.withRootType(TypeWithUnannotatedProperty, false, problems)

        when:
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.errors.empty
        validationContext.warnings.empty
    }

    def "handler functions gracefully when method is null"() {
        given:
        def context = Mock(TypeValidationContext)
        def annotationMetadata = Mock(PropertyAnnotationMetadata) {
            getPropertyName() >> "prop"
            getMethod() >> null
            isAnnotationPresent(Optional) >> false
        }

        when:
        MissingPropertyAnnotationHandler.MISSING_INPUT_OUTPUT_HANDLER.handleMissingPropertyAnnotation(
            context,
            annotationMetadata,
            "an input or output annotation"
        )

        then:
        1 * context.visitPropertyError(_)
    }
}
