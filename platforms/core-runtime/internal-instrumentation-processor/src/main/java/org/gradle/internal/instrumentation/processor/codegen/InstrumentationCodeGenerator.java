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

import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;

import java.util.Collection;
import java.util.List;

public interface InstrumentationCodeGenerator {

    GenerationResult generateCodeForRequestedInterceptors(
        Collection<CallInterceptionRequest> interceptionRequests
    );

    interface GenerationResult {

        interface CanGenerateClasses extends GenerationResult {
            Collection<String> getClassNames();
            void buildType(String className, TypeSpec.Builder builder);
            Collection<CallInterceptionRequest> getCoveredRequests();
        }

        class CodeFailures implements GenerationResult, HasFailures {
            private final List<FailureInfo> failures;

            public CodeFailures(List<FailureInfo> failures) {
                this.failures = failures;
            }

            @Override
            public List<FailureInfo> getFailureDetails() {
                return failures;
            }
        }
    }
}
