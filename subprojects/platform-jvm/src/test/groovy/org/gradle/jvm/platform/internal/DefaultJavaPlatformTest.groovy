/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.platform.internal

import org.gradle.api.JavaVersion
import spock.lang.Specification

class DefaultJavaPlatformTest extends Specification {

    def "is for current java version if not set"() {
        when:
        def platform = DefaultJavaPlatform.current()

        then:
        platform.targetCompatibility == JavaVersion.current()
    }

    def "can set java version"() {
        when:
        def platform = new DefaultJavaPlatform(JavaVersion.VERSION_1_5)

        then:
        platform.name == "java5"
        platform.displayName == "Java SE 5"
        platform.targetCompatibility == JavaVersion.VERSION_1_5
    }

    def "has reasonable string representation"() {
        when:
        def platform = DefaultJavaPlatform.current()

        then:
        platform.name == "java${JavaVersion.current().majorVersion}"
        platform.displayName == "Java SE ${JavaVersion.current().majorVersion}"
        platform.toString() == platform.displayName
    }
}
