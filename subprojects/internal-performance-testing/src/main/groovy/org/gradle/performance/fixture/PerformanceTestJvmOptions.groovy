/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion


@CompileStatic
class PerformanceTestJvmOptions {
    static List<String> customizeJvmOptions(List<? extends CharSequence> jvmOptions) {
        commonJvmOptions(jvmOptions, ['-Xms1g', '-Xmx1g'])
    }

    // JVM default options for both build JVM and the daemon client (launcher) JVM
    private static List<String> commonJvmOptions(List<? extends CharSequence> originalJvmOptions, List<String> defaultOptions = []) {
        List<String> jvmOptions
        if (!originalJvmOptions) {
            jvmOptions = defaultOptions
        } else {
            jvmOptions = originalJvmOptions.collect { it.toString() }  // makes sure that all elements are java.lang.String instances
        }
        if (!JavaVersion.current().isJava8Compatible() && jvmOptions.count { it.startsWith('-XX:MaxPermSize=') } == 0) {
            jvmOptions << '-XX:MaxPermSize=256m'
        }

        return jvmOptions
    }
}
