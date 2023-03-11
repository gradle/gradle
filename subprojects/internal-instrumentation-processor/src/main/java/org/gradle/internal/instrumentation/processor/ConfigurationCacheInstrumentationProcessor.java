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
import org.gradle.internal.instrumentation.api.annotations.UpgradedClassesRegistry;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeClassGenerator;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsGenerator;
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator;
import org.gradle.internal.instrumentation.processor.extensibility.ClassLevelAnnotationsContributor;
import org.gradle.internal.instrumentation.processor.extensibility.CodeGeneratorContributor;
import org.gradle.internal.instrumentation.processor.extensibility.InstrumentationProcessorExtension;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesExtra;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesPostProcessor;
import org.gradle.internal.instrumentation.processor.features.withstaticreference.WithExtensionReferencesReader;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationCallInterceptionRequestReaderImpl;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.gradle.internal.instrumentation.processor.AddGeneratedClassNameFlagFromClassLevelAnnotation.ifHasAnnotation;
import static org.gradle.internal.instrumentation.processor.AddGeneratedClassNameFlagFromClassLevelAnnotation.ifHasExtraOfType;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ConfigurationCacheInstrumentationProcessor extends AbstractInstrumentationProcessor {

    @Override
    protected Collection<InstrumentationProcessorExtension> getExtensions() {
        return Arrays.asList(
            (ClassLevelAnnotationsContributor) () -> Arrays.asList(SpecificJvmCallInterceptors.class, SpecificGroovyCallInterceptors.class),

            new AnnotationCallInterceptionRequestReaderImpl(),

            new WithExtensionReferencesReader(),
            new WithExtensionReferencesPostProcessor(),
            new AddGeneratedClassNameFlagFromClassLevelAnnotation(
                ifHasExtraOfType(WithExtensionReferencesExtra.ProducedSynthetically.class), SpecificJvmCallInterceptors.class, RequestExtra.InterceptJvmCalls::new
            ),

            new AddGeneratedClassNameFlagFromClassLevelAnnotation(ifHasAnnotation(InterceptJvmCalls.class), SpecificJvmCallInterceptors.class, RequestExtra.InterceptJvmCalls::new),
            new AddGeneratedClassNameFlagFromClassLevelAnnotation(ifHasAnnotation(InterceptGroovyCalls.class), SpecificGroovyCallInterceptors.class, RequestExtra.InterceptGroovyCalls::new),

            (CodeGeneratorContributor) InterceptJvmCallsGenerator::new,
            (CodeGeneratorContributor) InterceptGroovyCallsGenerator::new,

            // Properties upgrade extensions
            (ClassLevelAnnotationsContributor) () -> Collections.singletonList(UpgradedClassesRegistry.class),
            new PropertyUpgradeAnnotatedMethodReader(),
            (CodeGeneratorContributor) PropertyUpgradeClassGenerator::new
        );
    }
}
