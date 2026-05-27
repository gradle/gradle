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

import spock.lang.Specification

import java.security.CodeSource
import java.security.ProtectionDomain
import java.security.cert.Certificate

/**
 * Verifies resolution of the code-source file backing a protection domain.
 */
class ProtectionDomainsTest extends Specification {

    def "resolves the file of a file code source"() {
        given:
        def file = new File("/some/dir/lib.jar")
        def protectionDomain = protectionDomainFor(file.toURI().toURL())

        expect:
        ProtectionDomains.codeSourceFileOf(protectionDomain) == file
    }

    def "returns null for a null protection domain"() {
        expect:
        ProtectionDomains.codeSourceFileOf(null) == null
    }

    def "returns null when the code source has no location"() {
        given:
        def protectionDomain = new ProtectionDomain(new CodeSource(null, (Certificate[]) null), null)

        expect:
        ProtectionDomains.codeSourceFileOf(protectionDomain) == null
    }

    def "returns null for a non-file code source"() {
        given:
        def protectionDomain = protectionDomainFor(new URL("https://example.com/lib.jar"))

        expect:
        ProtectionDomains.codeSourceFileOf(protectionDomain) == null
    }

    private static ProtectionDomain protectionDomainFor(URL location) {
        new ProtectionDomain(new CodeSource(location, (Certificate[]) null), null)
    }
}
