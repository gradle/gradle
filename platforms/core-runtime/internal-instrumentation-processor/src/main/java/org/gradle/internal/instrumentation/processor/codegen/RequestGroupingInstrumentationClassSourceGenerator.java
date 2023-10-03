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
import org.gradle.internal.instrumentation.processor.codegen.HasFailures.FailureInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class RequestGroupingInstrumentationClassSourceGenerator implements InstrumentationCodeGenerator {
    protected abstract String classNameForRequest(CallInterceptionRequest request);

    protected abstract Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        Collection<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super FailureInfo> onFailure
    );

    @Override
    public GenerationResult generateCodeForRequestedInterceptors(Collection<CallInterceptionRequest> interceptionRequests) {
        LinkedHashMap<String, List<CallInterceptionRequest>> requestsByImplClass = interceptionRequests.stream()
            .filter(it -> classNameForRequest(it) != null)
            .collect(Collectors.groupingBy(this::classNameForRequest, LinkedHashMap::new, Collectors.toList()));

        List<FailureInfo> failuresInfo = new ArrayList<>();
        Set<CallInterceptionRequest> processedRequests = new LinkedHashSet<>(interceptionRequests.size());
        Map<String, Consumer<TypeSpec.Builder>> classContentByName = new LinkedHashMap<>();

        requestsByImplClass.forEach((className, requests) ->
            classContentByName.put(className, classContentForClass(className, requests, processedRequests::add, failuresInfo::add))
        );

        if (failuresInfo.isEmpty()) {
            return successResult(processedRequests, classContentByName);
        } else {
            return new GenerationResult.CodeFailures(failuresInfo);
        }
    }

    private static GenerationResult.CanGenerateClasses successResult(Set<CallInterceptionRequest> processedRequests, Map<String, Consumer<TypeSpec.Builder>> classContentByName) {
        return new GenerationResult.CanGenerateClasses() {
            @Override
            public Collection<String> getClassNames() {
                return classContentByName.keySet();
            }

            @Override
            public void buildType(String className, TypeSpec.Builder builder) {
                classContentByName.get(className).accept(builder);
            }

            @Override
            public List<CallInterceptionRequest> getCoveredRequests() {
                return new ArrayList<>(processedRequests);
            }
        };
    }
}
