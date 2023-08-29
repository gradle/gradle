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


import com.google.gson.Gson;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationResourceGenerator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Writes all instrumented properties to a resource file
 */
public class InstrumentedPropertiesResourceGenerator implements InstrumentationResourceGenerator {

    private final Gson gson = new Gson();

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
                return "META-INF/upgrades/upgraded-properties.json";
            }

            @Override
            public void write(OutputStream outputStream) {
                Map<String, List<CallInterceptionRequest>> requests = filteredRequests.stream()
                    .collect(groupingBy(InstrumentedPropertiesResourceGenerator::getFqName));
                List<PropertyEntry> entries = toPropertyEntries(requests);
                try (Writer writer = new OutputStreamWriter(outputStream)) {
                    writer.write(gson.toJson(entries));
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

    private static List<PropertyEntry> toPropertyEntries(Map<String, List<CallInterceptionRequest>> requests) {
        return requests.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> toPropertyEntry(e.getValue()))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static PropertyEntry toPropertyEntry(List<CallInterceptionRequest> requests) {
        CallInterceptionRequest request = requests.get(0);
        String propertyName = request.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class).get().getPropertyName();
        String containingType = request.getInterceptedCallable().getOwner().getType().getClassName();
        List<UpgradedMethod> upgradedMethods = requests.stream()
            .map(CallInterceptionRequest::getInterceptedCallable)
            .map(intercepted -> {
                Type returnType = intercepted.getReturnType().getType();
                Type[] parameterTypes = intercepted.getParameters().stream()
                    .map(ParameterInfo::getParameterType)
                    .toArray(Type[]::new);
                return new UpgradedMethod(intercepted.getCallableName(), Type.getMethodDescriptor(returnType, parameterTypes));
            })
            .sorted()
            .collect(Collectors.toList());
        return new PropertyEntry(containingType, propertyName, upgradedMethods);
    }

    static class PropertyEntry {
        private final String propertyName;
        private final String containingType;
        private final List<UpgradedMethod> upgradedMethods;

        public PropertyEntry(String containingType, String propertyName, List<UpgradedMethod> upgradedMethods) {
            this.containingType = containingType;
            this.propertyName = propertyName;
            this.upgradedMethods = upgradedMethods;
        }

        public String getContainingType() {
            return containingType;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public List<UpgradedMethod> getUpgradedMethods() {
            return upgradedMethods;
        }
    }

    static class UpgradedMethod implements Comparable<UpgradedMethod> {
        private final String name;
        private final String descriptor;

        public UpgradedMethod(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        @Override
        public int compareTo(@NotNull InstrumentedPropertiesResourceGenerator.UpgradedMethod o) {
            int nameComparison = name.compareTo(o.name);
            return nameComparison != 0 ? nameComparison : descriptor.compareTo(o.descriptor);
        }
    }
}
