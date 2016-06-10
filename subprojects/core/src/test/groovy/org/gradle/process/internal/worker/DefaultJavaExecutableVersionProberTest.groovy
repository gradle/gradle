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

package org.gradle.process.internal.worker

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.process.internal.JavaExecHandleBuilder
import spock.lang.Specification
import spock.lang.Subject


class DefaultJavaExecutableVersionProberTest extends Specification {
    @Subject
    DefaultJavaExecutableVersionProber prober = new DefaultJavaExecutableVersionProber()

    def "should parse output"() {
        expect:
        prober.findJavaVersion(['java version "1.8.0_91"', 'Java(TM) SE Runtime Environment (build 1.8.0_91-b14)', 'Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)']) == JavaVersion.VERSION_1_8
    }

    def "should parse output when _JAVA_OPTIONS is set"() {
        expect:
        prober.findJavaVersion(['Picked up _JAVA_OPTIONS: -Dset_by_java_options=1', 'java version "1.8.0_91"', 'Java(TM) SE Runtime Environment (build 1.8.0_91-b14)', 'Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)']) == JavaVersion.VERSION_1_8
    }

    def "should probe version of current JVM"() {
        expect:
        prober.probeVersion(new JavaExecHandleBuilder(TestFiles.resolver())) == JavaVersion.current()
    }
}
