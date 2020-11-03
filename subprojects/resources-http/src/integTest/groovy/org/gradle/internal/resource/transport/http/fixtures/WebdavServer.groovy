/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.resource.transport.http.fixtures

import de.bitinsomnia.webdav.server.MiltonHandler
import de.bitinsomnia.webdav.server.MiltonWebDAVResourceFactory
import io.milton.config.HttpManagerBuilder
import org.eclipse.jetty.server.Handler
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServerFixture
import org.junit.rules.ExternalResource

class WebdavServer extends ExternalResource implements HttpServerFixture {

    private final TestFile repositoryDirectory
    private final Map<String, String> credentials

    WebdavServer(TestDirectoryProvider testDirectoryProvider, Map<String, String> credentials = [:]) {
        this.repositoryDirectory = testDirectoryProvider.testDirectory
        this.credentials = credentials
    }

    @Override
    Handler getCustomHandler() {
        HttpManagerBuilder builder = new HttpManagerBuilder()

        builder.with {
            setResourceFactory(new MiltonWebDAVResourceFactory(repositoryDirectory, credentials))
            setEnableBasicAuth(credentials != null && !credentials.isEmpty())
        }

        return new MiltonHandler(builder.buildHttpManager())
    }
}
