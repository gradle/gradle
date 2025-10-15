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

package org.gradle.internal.instrumentation.processor;

import org.gradle.internal.instrumentation.api.annotations.InterceptGroovyCalls;
import org.gradle.internal.instrumentation.api.annotations.InterceptJvmCalls;
import org.gradle.internal.instrumentation.api.annotations.SpecificGroovyCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.SpecificJvmCallInterceptors;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.VisitForInstrumentation;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeClassSourceGenerator;
import org.gradle.internal.instrumentation.extensions.property.InstrumentedPropertiesResourceGenerator;
import org.gradle.internal.instrumentation.extensions.types.InstrumentedTypesResourceGenerator;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsGenerator;
import org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsResourceGenerator;
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator;
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsResourceGenerator;
import org.gradle.internal.instrumentation.processor.extensibility.ClassLevelAnnotationsContributor;
import org.gradle.internal.instrumentation.processor.extensibility.CodeGeneratorContributor;
import org.gradle.internal.instrumentation.processor.extensibility.InstrumentationProcessorExtension;
import org.gradle.internal.instrumentation.processor.extensibility.ResourceGeneratorContributor;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesExtra;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesPostProcessor;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesReader;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationCallInterceptionRequestReaderImpl;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import java.util.Arrays;
import java.util.Collection;

import static org.gradle.internal.instrumentation.processor.AddGeneratedClassNameFlagFromClassLevelAnnotation.ifHasAnnotation;
import static org.gradle.internal.instrumentation.processor.AddGeneratedClassNameFlagFromClassLevelAnnotation.ifHasExtraOfType;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ConfigurationCacheInstrumentationProcessor extends AbstractInstrumentationProcessor {

    @Override
    protected Collection<InstrumentationProcessorExtension> getExtensions() {
        return Arrays.asList(
            (ClassLevelAnnotationsContributor) () -> Arrays.asList(
                SpecificJvmCallInterceptors.class,
                SpecificGroovyCallInterceptors.class,
                VisitForInstrumentation.class,
                ReplacesEagerProperty.class,
                ToBeReplacedByLazyProperty.class
            ),

            new AnnotationCallInterceptionRequestReaderImpl(),

            new WithExtensionReferencesReader(),
            new WithExtensionReferencesPostProcessor(),
            new AddGeneratedClassNameFlagFromClassLevelAnnotation(processingEnv.getElementUtils(),
                ifHasExtraOfType(WithExtensionReferencesExtra.ProducedSynthetically.class), SpecificJvmCallInterceptors.class, RequestExtra.InterceptJvmCalls::new
            ),

            new AddGeneratedClassNameFlagFromClassLevelAnnotation(processingEnv.getElementUtils(),
                ifHasAnnotation(InterceptJvmCalls.class), SpecificJvmCallInterceptors.class, RequestExtra.InterceptJvmCalls::new
            ),
            new AddGeneratedClassNameFlagFromClassLevelAnnotation(processingEnv.getElementUtils(),
                ifHasAnnotation(InterceptGroovyCalls.class), SpecificGroovyCallInterceptors.class, RequestExtra.InterceptGroovyCalls::new
            ),

            (CodeGeneratorContributor) InterceptJvmCallsGenerator::new,
            // Generate META-INF/services resource with factories for all generated InterceptJvmCallsGenerator
            (ResourceGeneratorContributor) InterceptJvmCallsResourceGenerator::new,

            (CodeGeneratorContributor) InterceptGroovyCallsGenerator::new,
            // Generate META-INF/services resource with all generated CallInterceptors
            (ResourceGeneratorContributor) InterceptGroovyCallsResourceGenerator::new,

            // Properties upgrade extensions
            new PropertyUpgradeAnnotatedMethodReader(processingEnv),
            (CodeGeneratorContributor) PropertyUpgradeClassSourceGenerator::new,
            // Generate resource with instrumented properties
            (ResourceGeneratorContributor) InstrumentedPropertiesResourceGenerator::new,

            // Generate resource with instrumented types
            (ResourceGeneratorContributor) InstrumentedTypesResourceGenerator::new
        );
    }
}
