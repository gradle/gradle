/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.security.fixtures

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.security.internal.Fingerprint
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.gradle.security.internal.SecuritySupport.toLongIdHexString

class KeyServer extends HttpServer {

    private final TestFile baseDirectory
    private final Map<String, File> keyFiles = [:]

    KeyServer(TestFile baseDirectory) {
        this.baseDirectory = baseDirectory
        allow("/pks/lookup", false, ["GET"], new HttpServer.ActionSupport("Get key") {
            @Override
            void handle(HttpServletRequest request, HttpServletResponse response) {
                if (request.queryString.startsWith("op=get&options=mr&search=0x")) {
                    String keyId = request.queryString - "op=get&options=mr&search=0x"
                    if (KeyServer.this.keyFiles.containsKey(keyId)) {
                        KeyServer.this.fileHandler("/pks/lookup", KeyServer.this.keyFiles[keyId]).handle(request, response)
                    } else {
                        response.sendError(404, "not found")
                    }
                }
            }
        })
    }

    void registerPublicKey(PGPPublicKey key) {
        String longKeyId = toLongIdHexString(key.keyID)
        String fingerprint = Fingerprint.of(key).toString()
        def keyFile = baseDirectory.createFile("${longKeyId}.asc")
        keyFile.deleteOnExit()
        keyFile.newOutputStream().withCloseable { out ->
            new ArmoredOutputStream(out).withCloseable {
                key.encode(it)
            }
        }
        registerKey(longKeyId, keyFile)
        registerKey(fingerprint, keyFile)
    }

    private void registerKey(String keyId, File keyFile) {
        keyFiles[keyId] = keyFile
    }

    void withDefaultSigningKey() {
        File dir = baseDirectory.createDir("default-key")
        File publicKeyFile = new File(dir, "public-key.asc")
        publicKeyFile.deleteOnExit()
        SigningFixtures.writeValidPublicKeyTo(publicKeyFile)
        registerKey(SigningFixtures.validPublicKeyLongIdHexString, publicKeyFile)
        registerKey(SigningFixtures.validPublicKeyHexString, publicKeyFile)
    }

}
