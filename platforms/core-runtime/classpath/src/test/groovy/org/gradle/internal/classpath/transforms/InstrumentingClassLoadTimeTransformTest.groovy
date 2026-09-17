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

package org.gradle.internal.classpath.transforms

import org.gradle.internal.Pair
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import spock.lang.Specification
import spock.lang.TempDir

import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

/**
 * Verifies the class-load-time transform routes classes to the transform of their originating classpath entry
 * and leaves classes of unknown origin untouched.
 */
class InstrumentingClassLoadTimeTransformTest extends Specification {

    @TempDir
    File dir

    def "applies the transform of the originating classpath entry"() {
        given:
        def knownJar = new File(dir, "known.jar")
        def transformedEntries = []
        def entryTransform = Stub(ClassTransform) {
            apply(_, _, _) >> { entry, visitor, classData ->
                transformedEntries << entry.name
                Pair.of(entry.path, visitor)
            }
        }
        def transform = new InstrumentingClassLoadTimeTransform([(knownJar): entryTransform])

        when:
        def result = transform.transform(protectionDomainFor(knownJar), "Foo", trivialClassBytes("Foo"))

        then:
        transformedEntries == ["Foo.class"]
        result != null
    }

    def "leaves classes of unknown origin untouched"() {
        given:
        def knownJar = new File(dir, "known.jar")
        def transform = new InstrumentingClassLoadTimeTransform([(knownJar): Mock(ClassTransform)])
        def buffer = [1, 2, 3] as byte[]

        expect: "the buffer is returned as is, without even parsing it"
        transform.transform(protectionDomain, "Foo", buffer) === buffer

        where:
        protectionDomain << [
            protectionDomainFor(new File("unknown.jar")),
            null,
            new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null),
            new ProtectionDomain(null, null)
        ]
    }

    private static byte[] trivialClassBytes(String name) {
        def writer = new ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null)
        writer.visitEnd()
        writer.toByteArray()
    }

    private static ProtectionDomain protectionDomainFor(File file) {
        new ProtectionDomain(new CodeSource(file.toURI().toURL(), (Certificate[]) null), null)
    }
}
