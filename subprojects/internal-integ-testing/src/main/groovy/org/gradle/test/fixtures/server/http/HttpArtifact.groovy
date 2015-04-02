/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.fixtures.server.http

import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteArtifact

abstract class HttpArtifact extends HttpResource implements RemoteArtifact {

    String modulePath

    public HttpArtifact(HttpServer server, String modulePath) {
        super(server)
        this.modulePath = modulePath
    }

    HttpResource getMd5() {
        return new BasicHttpResource(server, getMd5File(), "${path}.md5")
    }

    HttpResource getSha1() {
        return new BasicHttpResource(server, getSha1File(), "${path}.sha1")
    }

    protected String getPath() {
        "${modulePath}/${file.name}"
    }

    protected abstract TestFile getSha1File();

    protected abstract TestFile getMd5File();

    abstract TestFile getFile();

    void verifyChecksums() {
        def sha1File = getSha1File()
        sha1File.assertIsFile()
        assert new BigInteger(sha1File.text, 16) == new BigInteger(getHash(getFile(), "sha1"), 16)
        def md5File = getMd5File()
        md5File.assertIsFile()
        assert new BigInteger(md5File.text, 16) == new BigInteger(getHash(getFile(), "md5"), 16)
    }

    protected String getHash(TestFile file, String algorithm) {
        HashUtil.createHash(file, algorithm.toUpperCase()).asHexString()
    }
}
