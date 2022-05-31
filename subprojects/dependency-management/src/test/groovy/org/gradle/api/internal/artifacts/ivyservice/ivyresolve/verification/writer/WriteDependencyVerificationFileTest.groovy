/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import spock.lang.Specification

import java.util.stream.Collectors

class WriteDependencyVerificationFileTest extends Specification {

    def "keys should be deduplicated when same keyid is present"() {
        given:
        def keyRings = [
            generateKeyRing(1),
            generateKeyRing(1),
            generateKeyRing(2)
        ]

        when:
        def publicKeys = WriteDependencyVerificationFile.collectDistinctPublicKeys(keyRings)

        then:
        publicKeys.size() == 2
        def keyIds = publicKeys.stream()
            .map(PGPPublicKey::getKeyID)
            .distinct()
            .sorted()
            .collect(Collectors.toList())
        keyIds.get(0) == 1
        keyIds.get(1) == 2
    }

    def generateKeyRing(long keyId) {
        PGPPublicKey publicKey = Mock {
            getKeyID() >> keyId
        }
        PGPPublicKeyRing publicKeyRing = Mock {
            getPublicKeys() >> [publicKey].iterator()
        }

        return publicKeyRing
    }

}
