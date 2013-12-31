/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.bintray

import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.plugin.resolve.internal.JCenterPluginMapper
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.rules.ExternalResource

class BintrayTestServer extends ExternalResource {

    private final HttpServer http

    final MavenHttpRepository jcenter
    final MavenFileRepository repo
    final BintrayApi api

    BintrayTestServer(TestFile repoDir) {
        http = new HttpServer()

        repo = new MavenFileRepository(repoDir)
        jcenter = new MavenHttpRepository(http, repo)
        api = new BintrayApi(http)
    }

    void start() {
        http.start()
        System.setProperty(JCenterPluginMapper.BINTRAY_API_OVERRIDE_URL_PROPERTY, api.address)
        System.setProperty(BaseRepositoryFactory.JCENTER_REPO_OVERRIDE_URL_PROPERTY, jcenter.uri.toString())
    }

    @Override
    protected void after() {
        System.clearProperty(JCenterPluginMapper.BINTRAY_API_OVERRIDE_URL_PROPERTY)
        System.clearProperty(BaseRepositoryFactory.JCENTER_REPO_OVERRIDE_URL_PROPERTY)
        http.stop()
    }
}
