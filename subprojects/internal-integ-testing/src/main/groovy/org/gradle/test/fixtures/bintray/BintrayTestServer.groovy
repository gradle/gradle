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

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.plugin.resolve.internal.JCenterPluginMapper
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.rules.ExternalResource

class BintrayTestServer extends ExternalResource {

    private final HttpServer http

    final MavenHttpRepository jcenter
    final MavenFileRepository repo
    final BintrayApi api

    BintrayTestServer(GradleExecuter executer, MavenFileRepository repo) {
        this.http = new HttpServer()
        this.repo = repo
        this.jcenter = new MavenHttpRepository(http, repo)
        this.api = new BintrayApi(http)

        executer.beforeExecute(new Action<GradleExecuter>() {
            void execute(GradleExecuter e) {
                if (http.running) {
                    e.withArguments(
                            "-D$JCenterPluginMapper.BINTRAY_API_OVERRIDE_URL_PROPERTY=$api.address",
                            "-D$BaseRepositoryFactory.JCENTER_REPO_OVERRIDE_URL_PROPERTY=$jcenter.uri"
                    )
                }
            }
        })
    }

    void start() {
        http.start()
    }

    @Override
    protected void after() {
        http.stop()
    }
}
