/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Abstraction to manage the {@link SecurityManager} in a way that works even if it is removed in future Java versions.
 */
@NullMarked
abstract class SecurityManagerRef {
    private static final boolean SECURITY_MANAGER_AVAILABLE;

    static {
        boolean isAvailable;
        try {
            Class.forName("java.lang.SecurityManager");
            isAvailable = true;
        } catch (ClassNotFoundException e) {
            isAvailable = false;
        }
        SECURITY_MANAGER_AVAILABLE = isAvailable;
    }

    /**
     * Acquire a security manager reference, or a fake one if {@link SecurityManager} has been removed.
     *
     * @return the reference
     */
    public static SecurityManagerRef getOrFake() {
        if (SECURITY_MANAGER_AVAILABLE) {
            return new RealSecurityManagerRef();
        }
        return new FakeSecurityManagerRef();
    }

    private static final class RealSecurityManagerRef extends SecurityManagerRef {
        private final SecurityManager reference = System.getSecurityManager();

        @Override
        public void reinstall(Logger logger) {
            if (System.getSecurityManager() != reference) {
                try {
                    System.setSecurityManager(reference);
                } catch (SecurityException e) {
                    logger.warn("Unable to reset SecurityManager. Continuing anyway...", e);
                }
            }
        }
    }

    private static final class FakeSecurityManagerRef extends SecurityManagerRef {
        @Override
        public void reinstall(Logger logger) {
            // No-op, as there is no security manager to reinstall
        }
    }

    /**
     * Reinstall the security manager if this is a real reference.
     * Catches and reports any {@link SecurityException} that may occur.
     *
     * @param logger the logger to report to
     */
    public abstract void reinstall(Logger logger);
}
