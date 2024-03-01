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

package org.gradle.internal.instrumentation.extensions.types;


import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Writes all instrumented types with inherited method interception to a resources
 */
public class InstrumentedTypesResourceGenerator implements InstrumentationResourceGenerator {
    @Override
    public Collection<CallInterceptionRequest> filterRequestsForResource(Collection<CallInterceptionRequest> interceptionRequests) {
        return interceptionRequests.stream()
            .filter(request -> request.getInterceptedCallable().getOwner().isInterceptSubtypes())
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
                return "META-INF/gradle/instrumentation/instrumented-classes.txt";
            }

            @Override
            public void write(OutputStream outputStream) {
                String types = filteredRequests.stream()
                    .map(request -> request.getInterceptedCallable().getOwner().getType().getClassName().replace(".", "/"))
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
