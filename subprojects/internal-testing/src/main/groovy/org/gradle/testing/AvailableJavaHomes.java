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
package org.gradle.testing;

import org.gradle.util.GFileUtils;
import org.gradle.util.Jvm;
import java.io.File;

/**
 * Allows the tests to get hold of an alternative JAVA_HOME when needed.
 *
 * This is used in the DaemonLifecycleSpec to test that daemons don't use difference JAVA_HOME's between client and browser.
 */
abstract public class AvailableJavaHomes {

    public static File getJavaHome(String label) {
        String value = System.getenv().get(getEnvVarName(label));
        return value == null ? null : GFileUtils.canonicalise(new File(value));
    }

    public static String getEnvVarName(String label) {
        return String.format("JDK_%s", label);
    }

    public static File getBestAlternative() {
        Jvm jvm = Jvm.current();
        if (jvm.isJava6Compatible()) {
            return firstAvailable("15", "17");
        } else if (jvm.isJava5Compatible()) {
            return firstAvailable("16", "17");
        } else {
            return null;
        }
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