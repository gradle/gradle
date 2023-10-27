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

package org.gradle.internal.instrumentation.processor.codegen;

import org.gradle.internal.instrumentation.model.CallInterceptionRequest;

import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

public interface InstrumentationResourceGenerator {

    /**
     * Filter the requests to only those that are relevant to the resource being generated.
     */
    Collection<CallInterceptionRequest> filterRequestsForResource(
        Collection<CallInterceptionRequest> interceptionRequests
    );

    /**
     * Actually generate the resource for filtered requests. A collection of filtered requests passed as a parameter will always have at least one element.
     */
    GenerationResult generateResourceForRequests(
        Collection<CallInterceptionRequest> filteredRequests
    );

    interface GenerationResult {
        interface CanGenerateResource extends GenerationResult {
            String getPackageName();
            String getName();
            void write(OutputStream outputStream);
        }

        class NoResourceToGenerate implements GenerationResult {
        }

        class ResourceFailures implements GenerationResult, HasFailures {
            private final List<FailureInfo> failures;

            public ResourceFailures(List<FailureInfo> failures) {
                this.failures = failures;
            }

            public List<FailureInfo> getFailureDetails() {
                return failures;
            }
        }
    }
}
