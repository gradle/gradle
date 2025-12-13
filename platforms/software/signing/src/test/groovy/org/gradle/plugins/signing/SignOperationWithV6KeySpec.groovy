/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.test.fixtures.file.TestFile

class SignOperationWithV6KeySpec extends SigningProjectSpec {

    TestFile testArtifactFile
    DefaultPublishArtifact testArtifact
    File testSignature

    def setup() {
        applyPlugin()
        addSigningProperties(set: "rfc9580v6sample")

        testArtifactFile = getResourceFile("1.txt")
        testArtifact = new DefaultPublishArtifact(testArtifactFile.name, "Text File", "txt", null, null, testArtifactFile)
        testSignature = signing.signatureType.fileFor(testArtifactFile)

        assert !testSignature.exists() || testSignature.delete()
        assert testArtifactFile.text // don't care what it is, just need some
    }

    def "sign single file with defaults"() {
        when:
        def operation = signing.sign(testArtifactFile)

        then:
        testSignature.exists()
        testSignature == operation.singleSignature.file
    }
}
