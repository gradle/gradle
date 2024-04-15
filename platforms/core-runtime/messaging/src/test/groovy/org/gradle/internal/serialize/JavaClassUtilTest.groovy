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

package org.gradle.internal.serialize

import spock.lang.Specification

class JavaClassUtilTest extends Specification {

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
