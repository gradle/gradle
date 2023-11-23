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

package org.gradle.internal.instrumentation.processor.codegen.jvmbytecode;

import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Generates META-INF/services resource with all factory classes for generated JvmBytecodeCallInterceptors so we can load them at runtime
 */
public class InterceptJvmCallsResourceGenerator implements InstrumentationResourceGenerator {
    @Override
    public Collection<CallInterceptionRequest> filterRequestsForResource(Collection<CallInterceptionRequest> interceptionRequests) {
        return interceptionRequests.stream()
            .filter(request -> request.getRequestExtras().getByType(RequestExtra.InterceptJvmCalls.class).isPresent())
            .collect(Collectors.toList());
    }

    @Override
    public GenerationResult generateResourceForRequests(Collection<CallInterceptionRequest> filteredRequests) {
        return new GenerationResult.CanGenerateResource() {
            @Override
            public String getPackageName() {
                return "";
            }

            @Override
            public String getName() {
                return "META-INF/services/" + JvmBytecodeCallInterceptor.Factory.class.getName();
            }

            @Override
            public void write(OutputStream outputStream) {
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                String types = filteredRequests.stream()
                    .map(request -> request.getRequestExtras().getByType(RequestExtra.InterceptJvmCalls.class))
                    .map(extra -> extra.get().getImplementationClassName() + "$Factory")
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("\n"));
                try (Writer writer = new OutputStreamWriter(outputStream)) {
                    writer.write(types);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
