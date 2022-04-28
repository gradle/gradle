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

package org.gradle.internal.upgrade;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.MethodVisitor;

import java.util.Optional;

public interface Replacement {
    /**
     * Replace the given instruction if it matches the replacement.
     *
     * @return {@code true} if the instruction has been replaced.
     */
    default boolean replaceByteCodeIfMatches(int opcode, String owner, String name, String desc, boolean itf, int index, MethodVisitor mv) {
        return false;
    }

    /**
     * Executes anything required to initialize the replacement.
     */
    default void initializeReplacement() {}

    /**
     * Decorate the given Groovy call site during runtime.
     */
    default Optional<CallSite> decorateCallSite(CallSite callSite) {
        return Optional.empty();
    }

    void applyConfigurationTo(Hasher hasher);

    static Object inferReceiverFromCallSiteReceiver(Object callSiteReceiver) {
        return callSiteReceiver instanceof Closure
            ? ((Closure<?>) callSiteReceiver).getDelegate()
            : callSiteReceiver;
    }
}
