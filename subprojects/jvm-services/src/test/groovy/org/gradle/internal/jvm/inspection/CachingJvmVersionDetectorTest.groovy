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

package org.gradle.internal.jvm.inspection

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.JavaInfo
import spock.lang.Specification

class CachingJvmVersionDetectorTest extends Specification {
    def "should cache results"() {
        given:
        def detector = Mock(JvmVersionDetector)
        def cachingDetector = new CachingJvmVersionDetector(detector)
        def javaInfo = Stub(JavaInfo) {
            getJavaExecutable() >> new File('/some/path/to/java')
        }
        when:
        cachingDetector.getJavaVersion(javaInfo)
        then:
        1 * detector.getJavaVersion(javaInfo) >> JavaVersion.VERSION_1_8
        0 * _._
        when:
        def version = cachingDetector.getJavaVersion(javaInfo)
        then:
        version == JavaVersion.VERSION_1_8
        0 * _._
    }
}
