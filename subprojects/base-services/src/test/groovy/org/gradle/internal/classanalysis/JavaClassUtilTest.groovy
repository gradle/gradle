/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classanalysis

import org.gradle.api.JavaVersion
import spock.lang.Specification

class JavaClassUtilTest extends Specification {

    def "can map java version to class file major version"() {
        expect:
        JavaClassUtil.getClassMajorVersion(javaVersion) == classFileVersion

        where:
        javaVersion             | classFileVersion
        JavaVersion.VERSION_1_1 | 45
        JavaVersion.VERSION_1_4 | 48
        JavaVersion.VERSION_1_5 | 49
        JavaVersion.VERSION_1_6 | 50
        JavaVersion.VERSION_1_7 | 51
        JavaVersion.VERSION_1_8 | 52
        JavaVersion.VERSION_11  | 55
        JavaVersion.VERSION_17  | 61
        JavaVersion.VERSION_18  | 62
        JavaVersion.VERSION_19  | 63
        JavaVersion.VERSION_20  | 64
    }

    def "cannot map higher java version to class file major version"() {
        when:
        JavaClassUtil.getClassMajorVersion(JavaVersion.VERSION_HIGHER)

        then:
        def ex = thrown(UnsupportedOperationException)
        ex.message == "Unable to provide class file major version for '${JavaVersion.VERSION_HIGHER}'"
    }

    def "can extract java class file major version"() {
        expect:
        JavaClassUtil.getClassMajorVersion(JavaClassUtil.class) == 50
    }

    def "can extract java class name major version"() {
        expect:
        JavaClassUtil.getClassMajorVersion(JavaClassUtil.class.getName(), JavaClassUtil.getClassLoader()) == 50
    }

    def "can extract java class input stream major version"() {
        expect:
        def stream = JavaClassUtil.getClassLoader().getResourceAsStream(JavaClassUtil.getName().replace(".", "/") + ".class")
        JavaClassUtil.getClassMajorVersion(stream) == 50
    }

}
