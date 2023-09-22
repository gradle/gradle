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

package org.gradle.internal.agents;

import javax.annotation.Nullable;
import java.security.ProtectionDomain;

/**
 * A ClassLoader implementing this interface gets a special treatment from the instrumenting Java agent.
 * When such a classloader attempts to define a class, the agent calls {@link #instrumentClass(String, ProtectionDomain, byte[])} method
 * giving the classloader a chance to rewrite the bytecode of the class.
 * <p>
 * Methods defined in this interface may be called concurrently in multiple threads.
 */
public interface InstrumentingClassLoader {
    /**
     * This hook is called when the class is being defined in this classloader.
     * If the implementation decides to replace the bytecode, it returns a non-null byte array from this method.
     * Prefer returning {@code null} instead of the {@code classfileBuffer} to continue loading the original bytecode.
     * <p>
     * Throwing the exception from this method aborts the transformation but doesn't affect class loading. The caller
     * (the agent) will catch all throwables thrown by this method and forward them to {@link #transformFailed(String, Throwable)}.
     * <p>
     * The {@code classfileBuffer} may be already modified by other Java agents.
     * <p>
     * This method may be called concurrently in multiple threads.
     *
     * @param className the name of the class being loaded (in the internal form, e.g. {@code java/util/List})
     * @param protectionDomain the protection domain of the original class, as defined by this classloader
     * @param classfileBuffer the buffer that contains class implementation bytes in class file format - must not be modified
     * @return new class implementation bytes in class file format or {@code null} to continue loading the original implementation
     */
    @Nullable
    byte[] instrumentClass(@Nullable String className, @Nullable ProtectionDomain protectionDomain, byte[] classfileBuffer);

    /**
     * This is called by the agent if a throwable is thrown while instrumenting a class during the call of the {@link #instrumentClass(String, ProtectionDomain, byte[])} method,
     * or anywhere else in the agent. Throwing an exception from this method has no effect on the class loading.
     * <p>
     * This method may be called concurrently in multiple threads.
     *
     * @param className the name of the class being loaded
     * @param th the throwable that appeared during class transformation
     */
    void transformFailed(@Nullable String className, Throwable th);
}
