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

import org.gradle.api.JavaVersion


class PerformanceTestJvmOptions {
    static List<String> customizeJvmOptions(List<String> jvmOptions) {
        if (!jvmOptions) {
            jvmOptions = ['-Xms2g', '-Xmx2g']
        } else {
            jvmOptions = jvmOptions.collect { it.toString() }
        }
        if (!JavaVersion.current().isJava8Compatible() && jvmOptions.count { it.startsWith('-XX:MaxPermSize=') } == 0) {
            jvmOptions << '-XX:MaxPermSize=256m'
        }
        jvmOptions << '-XX:+AlwaysPreTouch' // force the JVM to initialize heap at startup time to reduce jitter
        jvmOptions << '-XX:BiasedLockingStartupDelay=0' // start using biased locking when the JVM starts up
        jvmOptions << '-XX:CICompilerCount=2' // limit number of JIT compiler threads to reduce jitter
        return jvmOptions
    }
}
