/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Preconditions
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.junit.rules.ExternalResource
import org.mortbay.jetty.Handler
import org.mortbay.jetty.servlet.FilterHolder
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.servlet.RestFilter

class HttpBuildCacheServer extends ExternalResource implements HttpServerFixture {
    private final TestDirectoryProvider provider
    private final WebAppContext webapp
    private TestFile cacheDir
    private long dropConnectionForPutBytes = -1
    private int blockIncomingConnectionsForSeconds = 0

    HttpBuildCacheServer(TestDirectoryProvider provider) {
        this.provider = provider
        this.webapp = new WebAppContext()
    }

    TestFile getCacheDir() {
        Preconditions.checkNotNull(cacheDir)
    }

    @Override
    Handler getCustomHandler() {
        return webapp
    }

    private void addFilters() {
        if (dropConnectionForPutBytes > -1) {
            this.webapp.addFilter(new FilterHolder(new DropConnectionFilter(dropConnectionForPutBytes, this)), "/*", 1)
        }
        if (blockIncomingConnectionsForSeconds > 0) {
            this.webapp.addFilter(new FilterHolder(new BlockFilter(blockIncomingConnectionsForSeconds)), "/*", 1)
        }
        this.webapp.addFilter(RestFilter, "/*", 1)
    }

    void dropConnectionForPutAfterBytes(long numBytes) {
        this.dropConnectionForPutBytes = numBytes
    }

    @Override
    void start() {
        cacheDir = provider.testDirectory.createDir('http-cache-dir')
        webapp.resourceBase = cacheDir
        addFilters()
        HttpServerFixture.super.start()
    }

    @Override
    protected void after() {
        stop()
    }

    void withBasicAuth(String username, String password) {
        requireAuthentication('/*', username, password)
    }
}
