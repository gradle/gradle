/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.gradle.integtests.fixtures.jvm.InstalledJvmLocator;
import org.gradle.integtests.fixtures.jvm.JvmInstallation;
import org.gradle.internal.jvm.Jre;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.List;

/**
 * Allows the tests to get hold of an alternative Java installation when needed.
 */
abstract public class AvailableJavaHomes {
    private static File getJavaHome(String label) {
        String value = System.getenv().get(String.format("JDK_%s", label));
        return value == null ? null : GFileUtils.canonicalise(new File(value));
    }

    /**
     * Locates a JVM installation that is different to the current JVM.
     */
    public static File getBestAlternative() {
        Jvm jvm = Jvm.current();

        List<JvmInstallation> jvms = new InstalledJvmLocator().findJvms();
        for (JvmInstallation candidate : jvms) {
            if (!candidate.getJavaHome().equals(jvm.getJavaHome())) {
                return candidate.getJavaHome();
            }
        }

        // Use environment variables
        File javaHome = null;
        if (jvm.getJavaVersion().isJava6Compatible()) {
            javaHome = firstAvailable("15", "17");
        } else if (jvm.getJavaVersion().isJava5Compatible()) {
            javaHome = firstAvailable("16", "17");
        }
        if (javaHome != null) {
            return javaHome;
        }

        return null;
    }

    /**
     * Locates a JRE installation for the current JVM. Prefers a stand-alone JRE installation over one that is part of a JDK install.
     *
     * @return The JRE home directory, or null if not found
     */
    public static File getBestJre() {
        Jvm jvm = Jvm.current();
        Jre jre = jvm.getStandaloneJre();
        if (jre != null) {
            return jre.getHomeDir();
        }
        jre = jvm.getJre();
        if (jre != null) {
            return jre.getHomeDir();
        }
        return null;
    }

    public static File firstAvailable(String... labels) {
        for (String label : labels) {
            File found = getJavaHome(label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
