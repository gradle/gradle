/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.classpath.ClassLoadTimeTransform
import org.gradle.internal.classpath.TransformedClassPath
import spock.lang.Specification

import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

/**
 * Verifies the instrumenting classloader forwards the loaded class's protection domain to the class-load-time transform.
 */
class InstrumentingVisitableURLClassLoaderTest extends Specification {

    def "passes the protection domain of the loaded class to the class-load-time transform"() {
        given:
        def seen = []
        def transform = { ProtectionDomain protectionDomain, String className, byte[] classfileBuffer ->
            seen << protectionDomain
            classfileBuffer
        } as ClassLoadTimeTransform
        def original = new File("lib.jar")
        def classpath = TransformedClassPath.builderWithExactSize(1)
            .addUntransformed(original)
            .build()
            .withClassLoadTimeTransform(transform)
        def loader = VisitableURLClassLoader.fromClassPath("test", getClass().classLoader, classpath)
        def protectionDomain = new ProtectionDomain(new CodeSource(original.toURI().toURL(), (Certificate[]) null), null)

        when:
        ((InstrumentingClassLoader) loader).instrumentClass("com/example/Foo", protectionDomain, new byte[0])

        then:
        seen == [protectionDomain]
    }
}
