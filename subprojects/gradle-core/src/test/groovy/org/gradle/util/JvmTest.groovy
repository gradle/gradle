/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util

import spock.lang.Specification
import org.junit.Rule

class JvmTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    @Rule public final SetSystemProperties sysProp = new SetSystemProperties()

    def usesSystemPropertyToDetermineIfCompatibleWithJava5() {
        System.properties['java.version'] = '1.5'

        expect:
        def jvm = Jvm.current()
        jvm.java5Compatible
        !jvm.java6Compatible
    }

    def usesSystemPropertyToDetermineIfCompatibleWithJava6() {
        System.properties['java.version'] = '1.6'

        expect:
        def jvm = Jvm.current()
        jvm.java5Compatible
        jvm.java6Compatible
    }

    def usesSystemPropertyToDetermineIfAppleJvm() {
        System.properties['java.vm.vendor'] = 'Apple Inc.'

        expect:
        def jvm = Jvm.current()
        jvm.appleJvm
    }

    def appleJvmFiltersEnvironmentVariables() {
        Map<String, String> env = ['APP_NAME_1234': 'App', 'JAVA_MAIN_CLASS_1234': 'MainClass', 'OTHER': 'value']

        expect:
        new Jvm.AppleJvm().getInheritableEnvironmentVariables(env) == ['OTHER': 'value']
    }
}
