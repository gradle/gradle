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

import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.RequestExtra.InterceptJvmCalls;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class TypeHierarchyClassSourceGenerator extends RequestGroupingInstrumentationClassSourceGenerator {
    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(InterceptJvmCalls.class)
            .map(interceptJvmCalls -> interceptJvmCalls.getImplementationClassName() + "_TypeHierarchy")
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(String className, Collection<CallInterceptionRequest> requestsClassGroup, Consumer<? super CallInterceptionRequest> onProcessedRequest, Consumer<? super GenerationResult.HasFailures.FailureInfo> onFailure) {
        Set<String> upgradedTypes = new HashSet<>();
        for (CallInterceptionRequest request : requestsClassGroup) {
            if (request.getInterceptedCallable().getOwner().isInterceptSubtypes()) {
                upgradedTypes.add(request.getInterceptedCallable().getOwner().getType().getClassName());
            }
        }
        return null;
    }
}
