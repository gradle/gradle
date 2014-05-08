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

package org.gradle.test.fixtures.pluginportal

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.portal.PluginPortalResolver
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.ConfigureUtil
import org.gradle.util.GradleVersion
import org.junit.rules.ExternalResource

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.gradle.test.fixtures.server.http.HttpServer.Utils.json

class PluginPortalTestServer extends ExternalResource {

    private final HttpServer http

    final MavenHttpRepository m2repo

    PluginPortalTestServer(GradleExecuter executer, MavenFileRepository repo) {
        this.http = new HttpServer()
        this.m2repo = new MavenHttpRepository(http, repo)

        executer.beforeExecute(new Action<GradleExecuter>() {
            void execute(GradleExecuter e) {
                if (http.running) {
                    e.withArguments(
                            "-D$PluginPortalResolver.OVERRIDE_URL_PROPERTY=$http.address",
                    )
                }
            }
        })
    }

    void expectMissing(String pluginId, String version) {
        http.expectGetMissing("/api/gradle/${GradleVersion.current().version}/plugin/use/$pluginId/$version")
    }

    static class PluginUseResponse {
        String id
        String version

        static class Implementation {
            String gav
            String repo

            Implementation(String gav, String repo) {
                this.gav = gav
                this.repo = repo
            }
        }

        Implementation implementation
        String implementationType

        PluginUseResponse(String id, String version, Implementation implementation, String implementationType) {
            this.id = id
            this.version = version
            this.implementation = implementation
            this.implementationType = implementationType
        }
    }

    public MavenHttpModule expectPluginQuery(String pluginId, String pluginVersion, String group, String artifact, String version,
                                              @DelegatesTo(value = PluginUseResponse, strategy = Closure.DELEGATE_FIRST) Closure<?> configurer = null) {
        def useResponse = new PluginUseResponse(pluginId, pluginVersion, new PluginUseResponse.Implementation("$group:$artifact:$version", m2repo.uri.toString()), "M2_JAR")

        if (configurer) {
            ConfigureUtil.configure(configurer, useResponse)
        }

        http.expect("/api/gradle/${GradleVersion.current().version}/plugin/use/$pluginId/$pluginVersion", ["GET"], new HttpServer.ActionSupport("search action") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                json(response, useResponse)
            }
        })

        m2repo.module(group, artifact, version).publish()
    }

    public void expectPluginQuery(String pluginId, String pluginVersion, @DelegatesTo(value = HttpServletResponse, strategy = Closure.DELEGATE_FIRST) Closure<?> configurer) {
        http.expect("/api/gradle/${GradleVersion.current().version}/plugin/use/$pluginId/$pluginVersion", ["GET"], new HttpServer.ActionSupport("search action") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                ConfigureUtil.configure(configurer, response)
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
