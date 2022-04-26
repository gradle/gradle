package org.gradle.internal.upgrade;

import org.codehaus.groovy.runtime.callsite.CallSite;
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
}
