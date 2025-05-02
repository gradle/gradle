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

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteArtifact

abstract class HttpArtifact extends HttpResource implements RemoteArtifact {
    String modulePath

    public HttpArtifact(HttpServer server, String modulePath) {
        super(server)
        this.modulePath = modulePath
    }

    @Override
    HttpResource getMd5() {
        return new BasicHttpResource(server, getMd5File(), "${path}.md5")
    }

    @Override
    HttpResource getSha1() {
        return new BasicHttpResource(server, getSha1File(), "${path}.sha1")
    }

    @Override
    HttpResource getSha256() {
        new BasicHttpResource(server, getSha256File(), "${path}.sha256")
    }

    @Override
    HttpResource getSha512() {
        new BasicHttpResource(server, getSha512File(), "${path}.sha512")
    }

    @Override
    String getPath() {
        return "${modulePath}/${file.name}"
    }

    protected abstract TestFile getSha1File();

    protected abstract TestFile getMd5File();

    protected abstract TestFile getSha256File()

    protected abstract TestFile getSha512File()

    abstract TestFile getFile();

    void verifyChecksums() {
        if (server.supportsHash(HttpServer.SupportedHash.SHA1)) {
            def sha1File = getSha1File()
            sha1File.assertIsFile()
            assert HashCode.fromString(sha1File.text) == Hashing.sha1().hashFile(getFile())
        }
        if (server.supportsHash(HttpServer.SupportedHash.MD5)) {
            def md5File = getMd5File()
            md5File.assertIsFile()
            assert HashCode.fromString(md5File.text) == Hashing.md5().hashFile(getFile())
        }
    }

    void expectPublish(boolean extraChecksums = true, PasswordCredentials credentials = null) {
        expectPut(credentials)
        if (server.supportsHash(HttpServer.SupportedHash.SHA1)) {
            sha1.expectPut(credentials)
        }
        if (extraChecksums) {
            if (server.supportsHash(HttpServer.SupportedHash.SHA256)) {
                sha256.expectPut(credentials)
            }
            if (server.supportsHash(HttpServer.SupportedHash.SHA512)) {
                sha512.expectPut(credentials)
            }
        }
        if (server.supportsHash(HttpServer.SupportedHash.MD5)) {
            md5.expectPut(credentials)
        }
    }
}
