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

package org.gradle.internal.instrumentation.processor.features.withstaticreference;

import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.model.RequestExtrasContainer;
import org.gradle.internal.instrumentation.processor.extensibility.RequestPostProcessorExtension;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WithExtensionReferencesPostProcessor implements RequestPostProcessorExtension {

    @Override
    public Collection<CallInterceptionRequest> postProcessRequest(CallInterceptionRequest originalRequest) {
        Optional<WithExtensionReferencesExtra> extra = originalRequest.getRequestExtras().getByType(WithExtensionReferencesExtra.class);
        return extra
            .map(withExtensionReferencesExtra -> Arrays.asList(originalRequest, modifiedRequest(originalRequest, withExtensionReferencesExtra)))
            .orElseGet(() -> Collections.singletonList(originalRequest));
    }

    private static CallInterceptionRequest modifiedRequest(CallInterceptionRequest originalRequest, WithExtensionReferencesExtra extra) {
        return new CallInterceptionRequestImpl(
            modifiedCallableInfo(originalRequest.getInterceptedCallable(), extra),
            originalRequest.getImplementationInfo(),
            modifiedExtras(originalRequest.getRequestExtras())
        );
    }

    private static CallableInfo modifiedCallableInfo(CallableInfo originalInfo, WithExtensionReferencesExtra extra) {
        Type ownerType = extra.ownerType;
        String methodName = extra.methodName;
        return new CallableInfoImpl(CallableKindInfo.STATIC_METHOD, ownerType, methodName, originalInfo.getReturnType(), modifiedParameters(originalInfo.getParameters()));
    }

    private static List<ParameterInfo> modifiedParameters(List<ParameterInfo> originalParameters) {
        ArrayList<ParameterInfo> result = new ArrayList<>(originalParameters);
        if (result.size() == 0 || result.get(0).getKind() != ParameterKindInfo.RECEIVER) {
            throw new UnsupportedOperationException("extensions with static references that do not have a receiver parameter are not supported");
        }
        ParameterInfo originalReceiver = result.remove(0);
        result.add(0, new ParameterInfoImpl("receiverArg", originalReceiver.getParameterType(), ParameterKindInfo.METHOD_PARAMETER));
        return result;
    }

    private static List<RequestExtra> modifiedExtras(RequestExtrasContainer originalExtras) {
        return Stream.of(
            originalExtras.getAll().stream().filter(it -> !(it instanceof WithExtensionReferencesExtra)),
            Stream.<RequestExtra>of(new WithExtensionReferencesExtra.ProducedSynthetically())
        ).flatMap(Function.identity()).collect(Collectors.toList());
    }
}
