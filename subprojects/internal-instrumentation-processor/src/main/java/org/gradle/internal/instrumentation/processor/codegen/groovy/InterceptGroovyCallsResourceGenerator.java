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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.processor.codegen.groovy.InterceptGroovyCallsGenerator.CALL_INTERCEPTOR_CLASS;

public class InterceptGroovyCallsResourceGenerator implements InstrumentationResourceGenerator {
    @Override
    public GenerationResult generateResourceForRequestedInterceptors(Collection<CallInterceptionRequest> interceptionRequests) {
        List<CallInterceptionRequest> requests = interceptionRequests.stream()
            .filter(request -> request.getRequestExtras().getByType(RequestExtra.InterceptGroovyCalls.class).isPresent())
            .collect(Collectors.toList());

        if (requests.isEmpty()) {
            return new GenerationResult.NoResourceToGenerate();
        }

        return new GenerationResult.CanGenerateResource() {
            @Override
            public String getPackageName() {
                return "";
            }

            @Override
            public String getName() {
                return "META-INF/services/" + CALL_INTERCEPTOR_CLASS.canonicalName();
            }

            @Override
            public void write(OutputStream outputStream) {
                String types = requests.stream()
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

            @Override
            public Collection<CallInterceptionRequest> getCoveredRequests() {
                return requests;
            }
        };
    }
}
