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

import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

/**
 * Replaces a method call with alternative code.
 * <p>
 * Replaces both statically compiled calls and dynamic Groovy call sites.
 */
class MethodReplacement<T> implements Replacement {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodReplacement.class);

    private final String classType;
    private final Set<String> types;
    private final String methodName;
    private final Type[] argumentTypes;
    private final String methodDescriptor;
    private final ReplacementLogic<T> replacement;

    public MethodReplacement(String type, Collection<String> knownSubtypes, Type returnType, String methodName, Type[] argumentTypes, ReplacementLogic<T> replacement) {
        this.classType = type;
        this.types = Stream.concat(Stream.of(type), knownSubtypes.stream())
            .map(className -> className.replace('.', '/'))
            .collect(Collectors.toSet());
        this.methodName = methodName;
        this.argumentTypes = argumentTypes;
        this.methodDescriptor = Type.getMethodDescriptor(returnType, argumentTypes);
        this.replacement = replacement;
    }

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(MethodReplacement.class.getName());
        for (String type : types) {
            hasher.putString(type);
        }
        hasher.putString(methodName);
        hasher.putString(methodDescriptor);
        // TODO: Can we capture the replacements as well?
    }

    @Override
    public boolean replaceByteCodeIfMatches(int opcode, String owner, String name, String desc, boolean itf, int index, MethodVisitor mv) {
        if (opcode == INVOKEVIRTUAL
            && types.contains(owner)
            && name.equals(methodName)
            && desc.equals(methodDescriptor)) {
            LOGGER.info("Matched {}.{}({})", owner, name, desc);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<CallSite> decorateCallSite(CallSite callSite) {
        if (callSite.getName().equals(methodName)) {
            LOGGER.warn("Replacing CallSite for {} in {}", methodName, callSite.getArray().owner.getName());
            return Optional.empty();
        } else {
            return Optional.empty();
        }
    }
}
