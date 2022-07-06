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

import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

public class MethodReportableApiChange implements ReportableApiChange {

    private final Set<String> types;
    private final String methodName;
    private final String methodDescriptor;
    private final String displayText;
    private final String acceptation;
    private final List<String> changes;

    public MethodReportableApiChange(
        String type,
        Collection<String> knownSubtypes,
        Method method,
        String displayText,
        String acceptation,
        List<String> changes
    ) {
        this.types = Stream.concat(Stream.of(type), knownSubtypes.stream())
            .map(className -> className.replace('.', '/'))
            .collect(Collectors.toSet());
        this.methodName = method.getName();
        this.methodDescriptor = Type.getMethodDescriptor(method);
        this.displayText = displayText;
        this.acceptation = acceptation;
        this.changes = changes;
    }

    @Override
    public Optional<String> getApiChangeReportIfMatches(int opcode, String owner, String name, String desc) {
        if (opcode == INVOKEVIRTUAL
            && types.contains(owner)
            && name.equals(methodName)
            && desc.equals(methodDescriptor)) {
            return Optional.of("Method call '" + displayText + "', changes: " + changes + ", acceptation: " + acceptation);
        }
        return Optional.empty();
    }
}
