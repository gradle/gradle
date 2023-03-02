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
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.model.CallableKindInfo.AFTER_CONSTRUCTOR;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.PARAMETER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.RECEIVER_AS_CLASS;
import static org.gradle.internal.instrumentation.processor.codegen.groovy.ParameterMatchEntry.Kind.VARARG;

class SignatureTree {
    private CallInterceptionRequest leaf = null;
    private LinkedHashMap<ParameterMatchEntry, SignatureTree> childrenByMatchEntry = null;

    @Nullable
    public CallInterceptionRequest getLeafOrNull() {
        return leaf;
    }

    public Map<ParameterMatchEntry, SignatureTree> getChildrenByMatchEntry() {
        return childrenByMatchEntry != null ? childrenByMatchEntry : Collections.emptyMap();
    }

    void add(CallInterceptionRequest request) {
        CallableInfo callable = request.getInterceptedCallable();
        List<ParameterMatchEntry> matchEntries = parameterMatchEntries(callable);

        SignatureTree current = this;
        for (ParameterMatchEntry matchEntry : matchEntries) {
            if (current.childrenByMatchEntry == null) {
                current.childrenByMatchEntry = new LinkedHashMap<>();
            }
            if (matchEntry.kind == VARARG && current.childrenByMatchEntry.keySet().stream().anyMatch(it -> it.kind == VARARG)) {
                // TODO better diagnostics reporting
                throw new IllegalStateException("vararg overloads are not supported yet");
            }
            current = current.childrenByMatchEntry.computeIfAbsent(matchEntry, key -> new SignatureTree());
        }
        if (current.leaf != null) {
            // TODO better diagnostics reporting
            throw new IllegalStateException("duplicate request");
        }
        current.leaf = request;
    }

    @Nonnull
    private static List<ParameterMatchEntry> parameterMatchEntries(CallableInfo callable) {
        Optional<ParameterInfo> varargParameter = callable.getParameters().stream().filter(it -> it.getKind() == ParameterKindInfo.VARARG_METHOD_PARAMETER).findAny();
        return Stream.of(
            // Match the `Class<?>` in `receiver` for static methods and constructors
            callable.getKind() == CallableKindInfo.STATIC_METHOD || callable.getKind() == AFTER_CONSTRUCTOR
                ? Stream.of(new ParameterMatchEntry(callable.getOwner(), RECEIVER_AS_CLASS))
                : Stream.<ParameterMatchEntry>empty(),
            // Or match the receiver in the first parameter
            callable.getKind() == CallableKindInfo.INSTANCE_METHOD || callable.getKind() == CallableKindInfo.GROOVY_PROPERTY
                ? Stream.of(new ParameterMatchEntry(callable.getParameters().get(0).getParameterType(), RECEIVER))
                : Stream.<ParameterMatchEntry>empty(),
            // Then match the "normal" method parameters
            callable.getParameters().stream().filter(it -> it.getKind() == ParameterKindInfo.METHOD_PARAMETER).map(it -> new ParameterMatchEntry(it.getParameterType(), PARAMETER)),
            // In the end, match the vararg parameter, if it is there:
            varargParameter.map(parameterInfo -> Stream.of(new ParameterMatchEntry(parameterInfo.getParameterType().getElementType(), VARARG))).orElseGet(Stream::empty)
        ).flatMap(Function.identity()).collect(Collectors.toList());
    }
}
