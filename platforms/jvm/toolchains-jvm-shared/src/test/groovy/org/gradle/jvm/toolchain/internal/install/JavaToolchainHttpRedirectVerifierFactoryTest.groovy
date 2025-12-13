/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.install

import org.gradle.api.InvalidUserCodeException
import spock.lang.Specification

class JavaToolchainHttpRedirectVerifierFactoryTest extends Specification {

    JavaToolchainHttpRedirectVerifierFactory factory = new JavaToolchainHttpRedirectVerifierFactory()

    def "verifier created with secure URL"() {
        given:
        URI toolchainUri = URI.create("https://server.com")

        when:
        def verifier = factory.createVerifier(toolchainUri)

        then:
        verifier != null
    }

    def "fails creating verifier with insecure URL"() {
        given:
        URI toolchainUri = URI.create("http://insecure-server.com")

        when:
        factory.createVerifier(toolchainUri)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Attempting to download java toolchain from an insecure URI http://insecure-server.com. This is not supported, use a secure URI instead."
        e.cause == null
    }

    def "fails validating redirects with insecure URL"() {
        given:
        URI toolchainUri = URI.create("https://server.com")

        when:
        def verifier = factory.createVerifier(toolchainUri)
        verifier.validateRedirects([new URI("http://insecure-server.com")])

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Attempting to download java toolchain from an insecure URI http://insecure-server.com. This URI was reached as a redirect from https://server.com. " +
            "This is not supported, make sure no insecure URIs appear in the redirect"
        e.cause == null
    }
}
