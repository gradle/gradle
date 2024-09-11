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

package org.gradle.internal.instrumentation.extensions.property;


import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Writes all instrumented properties to a resource file
 */
public class InstrumentedPropertiesResourceGenerator implements InstrumentationResourceGenerator {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Collection<CallInterceptionRequest> filterRequestsForResource(Collection<CallInterceptionRequest> interceptionRequests) {
        return interceptionRequests.stream()
            .filter(request -> request.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class).isPresent())
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
                return "META-INF/gradle/instrumentation/upgraded-properties.json";
            }

            @Override
            public void write(OutputStream outputStream) {
                Map<String, List<CallInterceptionRequest>> requests = filteredRequests.stream()
                    .collect(groupingBy(InstrumentedPropertiesResourceGenerator::getFqName));
                List<UpgradedProperty> entries = toPropertyEntries(requests);
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    writer.write(mapper.writeValueAsString(entries));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static String getFqName(CallInterceptionRequest request) {
        String propertyName = request.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class).get().getPropertyName();
        String containingType = request.getInterceptedCallable().getOwner().getType().getClassName();
        return containingType + "#" + propertyName;
    }

    private static List<UpgradedProperty> toPropertyEntries(Map<String, List<CallInterceptionRequest>> requests) {
        return requests.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> toPropertyEntry(e.getValue()))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static UpgradedProperty toPropertyEntry(List<CallInterceptionRequest> requests) {
        CallInterceptionRequest firstRequest = requests.get(0);
        PropertyUpgradeRequestExtra upgradeExtra = firstRequest.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class).get();
        String propertyName = upgradeExtra.getPropertyName();
        String methodName = upgradeExtra.getMethodName();
        String methodDescriptor = upgradeExtra.getMethodDescriptor();
        String containingType = firstRequest.getInterceptedCallable().getOwner().getType().getClassName();
        List<ReplacedAccessor> upgradedAccessors = requests.stream()
            .map(request -> {
                PropertyUpgradeRequestExtra requestExtra = request.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class).get();
                CallableInfo intercepted = request.getInterceptedCallable();
                Type returnType = intercepted.getReturnType().getType();
                Type[] parameterTypes = intercepted.getParameters().stream()
                    .map(ParameterInfo::getParameterType)
                    .toArray(Type[]::new);
                return new ReplacedAccessor(intercepted.getCallableName(), Type.getMethodDescriptor(returnType, parameterTypes), requestExtra.getBinaryCompatibility());
            })
            .sorted(Comparator.comparing((ReplacedAccessor o) -> o.name).thenComparing(o -> o.descriptor))
            .collect(Collectors.toList());
        return new UpgradedProperty(containingType, propertyName, methodName, methodDescriptor, upgradedAccessors);
    }

    @JsonPropertyOrder(alphabetic = true)
    static class UpgradedProperty {
        private final String propertyName;
        private final String methodName;
        private final String methodDescriptor;
        private final String containingType;
        private final List<ReplacedAccessor> replacedAccessors;

        public UpgradedProperty(String containingType, String propertyName, String methodName, String methodDescriptor, List<ReplacedAccessor> replacedAccessors) {
            this.containingType = containingType;
            this.propertyName = propertyName;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.replacedAccessors = replacedAccessors;
        }

        public String getContainingType() {
            return containingType;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodDescriptor() {
            return methodDescriptor;
        }

        public List<ReplacedAccessor> getReplacedAccessors() {
            return replacedAccessors;
        }
    }

    @JsonPropertyOrder(alphabetic = true)
    static class ReplacedAccessor {
        private final String name;
        private final String descriptor;
        private final BinaryCompatibility binaryCompatibility;

        public ReplacedAccessor(String name, String descriptor, BinaryCompatibility binaryCompatibility) {
            this.name = name;
            this.descriptor = descriptor;
            this.binaryCompatibility = binaryCompatibility;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public BinaryCompatibility getBinaryCompatibility() {
            return binaryCompatibility;
        }
    }
}
