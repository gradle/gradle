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
    static List<String> customizeJvmOptions(List<String> jvmOptions) {
        commonJvmOptions(jvmOptions, ['-Xms2g', '-Xmx2g'])
    }

    // JVM default options for both build JVM and the daemon client (launcher) JVM
    private static List<String> commonJvmOptions(List<String> originalJvmOptions, List<String> defaultOptions = []) {
        List<String> jvmOptions
        if (!originalJvmOptions) {
            jvmOptions = defaultOptions
        } else {
            jvmOptions = originalJvmOptions.collect { it.toString() }  // makes sure that all elements are java.lang.String instances
        }
        if (!JavaVersion.current().isJava8Compatible() && jvmOptions.count { it.startsWith('-XX:MaxPermSize=') } == 0) {
            jvmOptions << '-XX:MaxPermSize=256m'
        }
        jvmOptions << '-XX:+PerfDisableSharedMem' // reduce possible jitter caused by slow /tmp
        jvmOptions << '-XX:+AlwaysPreTouch' // force the JVM to initialize heap at startup time to reduce jitter
        jvmOptions << '-XX:BiasedLockingStartupDelay=0' // start using biased locking when the JVM starts up
        jvmOptions << '-XX:CICompilerCount=2' // limit number of JIT compiler threads to reduce jitter
        jvmOptions << '-Dsun.io.useCanonCaches=false' // disable JVM file canonicalization cache since the caching might cause jitter in tests

        if (JavaVersion.current().isJava8Compatible()) {
            // settings to reduce malloc calls and to reduce native memory fragmentation

            // code cache allocation settings
            jvmOptions << '-XX:InitialCodeCacheSize=64m'
            jvmOptions << '-XX:CodeCacheExpansionSize=4m'
            jvmOptions << '-XX:ReservedCodeCacheSize=256m' // specifying this setting is required if InitialCodeCacheSize is set

            // metaspace allocation settings
            jvmOptions << '-XX:MetaspaceSize=64m'
            jvmOptions << '-XX:MinMetaspaceExpansion=4m'
            jvmOptions << '-XX:MaxMetaspaceExpansion=32m'
        }

        return jvmOptions
    }

    // JVM options for daemon client (launcher)
    static List<String> createDaemonClientJvmOptions() {
        commonJvmOptions(['-Xms512m', '-Xmx512m', '-Xverify:none', '-Xshare:auto'])
    }
}
