/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize;

import org.jspecify.annotations.NullMarked;

import java.io.InvalidClassException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A denylist of classes that are refused during Java deserialization.
 * <p>
 * These are the well-known "sink" classes of published deserialization gadget chains — the endpoints whose
 * deserialization has a dangerous side effect (loading bytecode, opening a JDBC/JNDI/RMI connection, and so on).
 */
@NullMarked
public final class UnsafeDeserializationClasses {

    private static final Set<String> BLOCKED_NAMES = new HashSet<String>(Arrays.asList(
        "java.security.SignedObject",
        "sun.reflect.annotation.AnnotationInvocationHandler",
        "org.apache.commons.beanutils.BeanComparator",
        "org.codehaus.groovy.runtime.MethodClosure",
        "org.codehaus.groovy.runtime.ConversionHandler"
    ));

    private static final String[] BLOCKED_PREFIXES = {
        "com.sun.org.apache.xalan.",
        "com.sun.org.apache.xpath.",
        "com.sun.rowset.",
        "org.apache.commons.collections.functors.",
        "org.apache.commons.collections4.functors.",
        "com.mchange.v2.c3p0.",
        "bsh.",
        "org.python.core.",
        "clojure.lang."
    };

    private UnsafeDeserializationClasses() {
    }

    /**
     * @throws InvalidClassException if the class is on the denylist, before it is resolved or instantiated.
     */
    public static void checkNotBlocked(String className) throws InvalidClassException {
        if (isBlocked(className)) {
            throw new InvalidClassException(className, "Refused to deserialize a class that is known to be unsafe");
        }
    }

    private static boolean isBlocked(String className) {
        if (BLOCKED_NAMES.contains(className)) {
            return true;
        }
        for (String prefix : BLOCKED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

}
