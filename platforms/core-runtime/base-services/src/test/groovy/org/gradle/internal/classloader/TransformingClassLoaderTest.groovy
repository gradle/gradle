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

package org.gradle.internal.classloader

import spock.lang.Specification

class TransformingClassLoaderTest extends Specification {
    def cl  = new TransformingClassLoader("test", null, []) {
        @Override
        protected byte[] transform(String className, byte[] bytes) {
            return bytes
        }
    }

    def "throws CNFE for unknown class"() {
        when:
        cl.loadClass("org.gradle.unknown.Unknown")

        then:
        def e = thrown(ClassNotFoundException)
        e.message.contains("org.gradle.unknown.Unknown")
    }
}
