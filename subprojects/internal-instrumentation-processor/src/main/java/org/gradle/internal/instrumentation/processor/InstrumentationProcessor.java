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
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsGenerator;
import org.gradle.internal.instrumentation.processor.codegen.jvmbytecode.InterceptJvmCallsGenerator;
import org.gradle.internal.instrumentation.processor.extensibility.ClassLevelAnnotationsContributor;
import org.gradle.internal.instrumentation.processor.extensibility.CodeGeneratorContributor;
import org.gradle.internal.instrumentation.processor.extensibility.InstrumentationProcessorExtension;
import org.gradle.internal.instrumentation.processor.modelreader.AnnotationCallInterceptionRequestReaderImpl;

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import java.util.Arrays;
import java.util.Collection;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class InstrumentationProcessor extends AbstractInstrumentationProcessor {

    @Override
    protected Collection<InstrumentationProcessorExtension> getExtensions() {
        return Arrays.asList(
            (ClassLevelAnnotationsContributor) () -> Arrays.asList(SpecificJvmCallInterceptors.class, SpecificGroovyCallInterceptors.class),

            new AnnotationCallInterceptionRequestReaderImpl(),

            new AddGeneratedClassNameFlagFromClassLevelAnnotation(InterceptJvmCalls.class, SpecificJvmCallInterceptors.class, RequestExtra.InterceptJvmCalls::new),
            new AddGeneratedClassNameFlagFromClassLevelAnnotation(InterceptGroovyCalls.class, SpecificGroovyCallInterceptors.class, RequestExtra.InterceptGroovyCalls::new),

            (CodeGeneratorContributor) InterceptJvmCallsGenerator::new,
            (CodeGeneratorContributor) InterceptGroovyCallsGenerator::new
        );
    }
}
