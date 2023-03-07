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

package org.gradle.security.internal

import spock.lang.Specification

class SecuritySupportSpec extends Specification {

    def "strips data from saved keys on read"() {
        given:
        def keyringFile = new File(this.class.getResource("/keyrings/valid-with-extra-metadata.keys").getFile())

        when:
        def keyrings = SecuritySupport.loadKeyRingFile(keyringFile)

        then:
        keyrings.size() == 3
        keyrings.forEach { keyRing ->
            keyRing.publicKeys.forEachRemaining { publicKey ->
                assert publicKey.getUserAttributes().size() == 0
                assert publicKey.signatures.size() == publicKey.keySignatures.size()
                assert publicKey.userIDs.size() == 0
            }
        }
    }


}
