/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.toolchain;

import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.File;

/**
 * Criteria for selecting a JVM for the daemon. This may be as straightforward as a specific Java home, or more complex, such as a specific version of the JVM.
 */
// Implementation note: This works like a sealed interface, so any additional subclasses need to be checked anywhere DaemonJvmCriteria is `instanceof`'d to a subclass.
public interface DaemonJvmCriteria {
    /**
     * Probes the Java language version of the JVM criteria. This may need to launch a JVM to determine the version.
     *
     * @param detector The detector to use to probe the JVM if needed
     * @return The Java language version of the JVM criteria
     */
    JavaLanguageVersion probeJavaLanguageVersion(JvmVersionDetector detector);

    /**
     * Selects the current JVM, known as the Launcher JVM.
     */
    final class LauncherJvm implements DaemonJvmCriteria {
        @Override
        public JavaLanguageVersion probeJavaLanguageVersion(JvmVersionDetector detector) {
            return JavaLanguageVersion.current();
        }

        @Override
        public String toString() {
            return Jvm.current().getJavaHome().getAbsolutePath() + " (no JDK specified, using current Java home)";
        }
    }

    /**
     * Selects the specified Java home.
     */
    final class JavaHome implements DaemonJvmCriteria {
        public enum Source {
            /**
             * The Java home was specified by the user using the system property `org.gradle.java.home`.
             */
            ORG_GRADLE_JAVA_HOME("org.gradle.java.home"),
            /**
             * The Java home was specified by a Tooling API client.
             */
            TOOLING_API_CLIENT("Tooling API client"),
            /**
             * The Java home comes from an existing daemon.
             */
            EXISTING_DAEMON("existing daemon");

            private final String description;

            Source(String description) {
                this.description = description;
            }
        }

        private final Source source;
        private final File javaHome;

        public JavaHome(Source source, File javaHome) {
            // Sanity check the Java home
            if (!javaHome.isDirectory()) {
                throw new IllegalArgumentException("Java home '" + javaHome.getAbsolutePath() + "' is not a directory");
            }
            Jvm.forHome(javaHome); // Throws an exception if the Java home is invalid
            this.source = source;
            this.javaHome = javaHome;
        }

        public File getJavaHome() {
            return javaHome;
        }

        @Override
        public JavaLanguageVersion probeJavaLanguageVersion(JvmVersionDetector detector) {
            return JavaLanguageVersion.of(detector.getJavaVersionMajor(Jvm.forHome(javaHome)));
        }

        @Override
        public String toString() {
            return String.format("%s (from %s)", getJavaHome().getAbsolutePath(), source.description);
        }
    }

    /**
     * Selects a JVM based on the given restrictions.
     */
    final class Spec implements DaemonJvmCriteria {
        private final JavaLanguageVersion javaVersion;
        private final JvmVendorSpec vendorSpec;
        private final JvmImplementation jvmImplementation;

        public Spec(JavaLanguageVersion javaVersion, JvmVendorSpec vendorSpec, JvmImplementation jvmImplementation) {
            this.javaVersion = javaVersion;
            this.vendorSpec = vendorSpec;
            this.jvmImplementation = jvmImplementation;
        }

        public JavaLanguageVersion getJavaVersion() {
            return javaVersion;
        }

        public JvmVendorSpec getVendorSpec() {
            return vendorSpec;
        }

        public JvmImplementation getJvmImplementation() {
            return jvmImplementation;
        }

        public boolean isCompatibleWith(Jvm other) {
            Integer javaVersionMajor = other.getJavaVersionMajor();
            if (javaVersionMajor == null) {
                return false;
            }
            return isCompatibleWith(JavaLanguageVersion.of(javaVersionMajor), other.getVendor());
        }

        public boolean isCompatibleWith(JavaLanguageVersion javaVersion, String javaVendor) {
            return javaVersion.equals(getJavaVersion()) && vendorSpec.matches(javaVendor);
        }

        @Override
        public JavaLanguageVersion probeJavaLanguageVersion(JvmVersionDetector detector) {
            return getJavaVersion();
        }

        @Override
        public String toString() {
            return String.format("Compatible with Java %s, %s (from %s)", getJavaVersion(), getVendorSpec(), DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE);
        }
    }
}
