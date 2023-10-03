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
import org.gradle.internal.instrumentation.model.RequestExtra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RequestValidationUtils {

    public static RequestExtra.InterceptionType getAndValidateInterceptionType(String className, Collection<CallInterceptionRequest> requestsClassGroup, Class<? extends RequestExtra.HasInterceptionType> typeWithInterceptionType, Consumer<? super HasFailures.FailureInfo> onFailure) {
        List<RequestExtra.HasInterceptionType> interceptJvmCalls = requestsClassGroup.stream()
            .map(r -> r.getRequestExtras().getByType(typeWithInterceptionType))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        validateRequestAreForTheSameInterception(className, interceptJvmCalls, onFailure);
        return interceptJvmCalls.iterator().next().getInterceptionType();
    }

    private static void validateRequestAreForTheSameInterception(String className, Collection<? extends RequestExtra.HasInterceptionType> requests, Consumer<? super HasFailures.FailureInfo> onFailure) {
        if (requests.stream().map(RequestExtra.HasInterceptionType::getInterceptionType).distinct().count() > 1) {
            onFailure.accept(new HasFailures.FailureInfo(null,"Generated class '" + className + "' does instrumentation and bytecode upgrades at the same time. This is not supported!"));
        }
    }
}
