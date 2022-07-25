/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

public class MethodReportableApiChange implements ReportableApiChange {

    private final String type;
    private final List<String> parameterTypes;
    private final Set<String> types;
    private final String methodName;
    private final String methodDescriptor;
    private final String displayText;
    private final String acceptation;
    private final List<String> changes;

    public MethodReportableApiChange(
        String type,
        List<String> parameterTypes,
        Collection<String> knownSubtypes,
        String methodName,
        String methodDescriptor,
        String displayText,
        String acceptation,
        List<String> changes
    ) {
        this.type = type;
        this.parameterTypes = parameterTypes;
        this.types = Stream.concat(Stream.of(type), knownSubtypes.stream())
            .map(className -> className.replace('.', '/'))
            .collect(Collectors.toSet());
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.displayText = displayText;
        this.acceptation = acceptation;
        this.changes = changes;
    }

    @Override
    public String getApiChangeReport() {
        return getChangeReport();
    }

    @Override
    public List<ApiMatcher> getMatchers() {
        return types.stream()
            .map(type -> new ApiMatcher(INVOKEVIRTUAL, type, methodName, methodDescriptor))
            .collect(Collectors.toList());
    }

    private String getChangeReport() {
        return "Method call '" + displayText + "', changes: " + changes + ", acceptation: " + acceptation;
    }

    @Override
    public Optional<DynamicGroovyUpgradeDecoration> mapToDynamicGroovyDecoration(ApiUpgradeProblemCollector problemCollector) {
        // TODO, we should probably rather check changes that should say that something is a property upgrade
        if (!"Property upgraded".equals(this.acceptation)) {
            throw new UnsupportedOperationException("Unsupported upgrade: " + this.displayText);
        }
        if ((methodName.startsWith("is") || methodName.startsWith("get")) && parameterTypes.isEmpty()) {
            String propertyName = extractPropertyName(methodName);
            return Optional.of(new DynamicGroovyPropertyUpgradeDecoration(problemCollector, type, propertyName, this::getChangeReport));
        }
        return Optional.empty();
    }

    private String extractPropertyName(String methodName) {
        String capitalizedProperty;
        if (methodName.startsWith("is")) {
            capitalizedProperty = methodName.replace("is", "");
        } else {
            capitalizedProperty = methodName.replace("get", "");
        }
        // TODO maybe we need smarter algorithm here
        return capitalizedProperty.substring(0, 1).toLowerCase() + capitalizedProperty.substring(1);
    }
}
