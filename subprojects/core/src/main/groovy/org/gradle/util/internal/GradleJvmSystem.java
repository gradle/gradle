/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.internal;

import java.security.Permission;

/**
 * It's the only official way of exiting Gradle processes.
 * We should use it internally instead of System.exit() so that we can bypass our own SecurityManager
 * that protects from the 3rd party System.exit()
 * <p>
 * The reason we need that is because when 3rd party plugin, library or code performs System.exit()
 * then the vm disappears pretty quickly and the problem is very hard to track down. Especially when running with a daemon.
 * <p>
 * Uses System.out for warnings instead of Logger because most operations happen (or may happen) very early, before we initialize the logging.
 * This should be fine because the warnings cover true edge cases.
 * <p>
 * The implementation is not thread-safe.
 * <p>
 * by Szczepan Faber, created at: 3/2/12
 */
public class GradleJvmSystem {

    /**
     * Installs a SecurityManager that adds some extra protection.
     * If already installed this method does nothing.
     */
    public static void installSecurity() {
        if (isGradleSecurityManager(System.getSecurityManager())) {
            return;
        }

        try {
            System.setSecurityManager(new GradleSecurityManager());
        } catch (SecurityException e) {
            //In majority of cases it should not happen.
            //However, should someone try to innovate, experiment or simply use some awkward jvm
            //let's be lenient in case the security manager cannot be installed.
            System.out.println("Warning: Unable to install Gradle security. Some protection may be disabled. Problem: " + e);
        }
    }

    public static void exit(int exitValue) {
        SecurityManager current = System.getSecurityManager();
        if (isGradleSecurityManager(current)) {
            System.setSecurityManager(null);
        } else {
            System.out.println("Warning: System.exit() requested but Gradle security manager was not installed or was replaced. "
                    + "Gradle does not know if the request is a legitimate one.");
        }
        System.exit(exitValue);
    }

    private static boolean isGradleSecurityManager(SecurityManager manager) {
        //not using instanceof to avoid problems when isolated classloaders are involved.
        return manager != null && manager.getClass().getName().equals(GradleSecurityManager.class.getName());
    }

    public static class GradleSecurityManager extends SecurityManager {

        private static final String SYSTEM_EXIT_NOT_PERMITTED =
                "System.exit() detected. Gradle does not permit 3rd party plugins or client builds to perform the vm exit.\n"
                + "The reason is that disappearing vm leads to problems that are very hard to diagnose, "
                + "especially when running with the gradle build daemon.";

        public void checkPermission(Permission permission) {
            //by default we allow everything because the default security manager is null.
        }

        public void checkExit(int i) {
            throw new SecurityException(SYSTEM_EXIT_NOT_PERMITTED);
        }

    }
}
