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

package org.gradle.security.internal

import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers
import org.bouncycastle.openpgp.PGPPublicKey
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * The distribution ships a minified BouncyCastle provider without its post-quantum algorithms, which is
 * only sound as long as OpenPGP does not use them. RFC 9980 defines how it could, so this states the
 * assumption: when an upgrade starts to honour it, this fails instead of the distribution.
 */
class BouncyCastlePostQuantumSpec extends Specification {

    def "#library does not refer to post-quantum cryptography"() {
        given:
        def jar = jarOf(probe)

        expect:
        classesReferring(jar, "org/bouncycastle/pqc").isEmpty()

        where:
        library  | probe
        "bcpg"   | PGPPublicKey
        "bcutil" | CMPObjectIdentifiers
    }

    private static File jarOf(Class<?> type) {
        def location = new File(type.protectionDomain.codeSource.location.toURI())
        assert location.name.endsWith(".jar"): "$type is not loaded from a Jar, but from $location"
        assert !location.name.endsWith("-min.jar"): "$location is minified, but the test should run against the unminified version"
        location
    }

    private static List<String> classesReferring(File jar, String reference) {
        new ZipFile(jar).withCloseable { zip ->
            zip.entries().toList()
                .findAll { it.name.endsWith(".class") }
                .findAll { new String(zip.getInputStream(it).bytes, StandardCharsets.ISO_8859_1).contains(reference) }
                *.name
        }
    }
}
