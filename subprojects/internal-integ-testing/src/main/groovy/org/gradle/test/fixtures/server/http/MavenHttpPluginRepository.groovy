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

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.util.ConfigureUtil
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.gradle.test.fixtures.plugin.PluginBuilder.PLUGIN_MARKER_SUFFIX

class MavenHttpPluginRepository extends MavenHttpRepository implements HttpPluginRepository, TestRule {

    static MavenHttpPluginRepository asGradlePluginPortal(GradleExecuter executer, MavenFileRepository backingRepository) {
        MavenHttpPluginRepository pluginRepo = new MavenHttpPluginRepository(backingRepository)
        pluginRepo.configure(executer)
        return pluginRepo
    }

    MavenHttpPluginRepository(MavenFileRepository backingRepository) {
        super(new HttpServer(), "/m2", backingRepository)
    }

    public <T extends GradleExecuter> T configure(T executer) {
        executer.beforeExecute(new Action<GradleExecuter>() {
            void execute(GradleExecuter e) {
                if (server.running) {
                    e.withArgument("-D${BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${uri.toString()}")
                }
            }
        })
        executer
    }


    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                start()
                try {
                    base.evaluate()
                } finally {
                    stop()
                }
            }
        }
    }

    void start() {
        server.start()
    }

    void stop() {
        server.stop()
    }

    @Override
    void expectPluginMarkerMissing(String pluginId, String pluginVersion) {
        module(pluginId, pluginId + PLUGIN_MARKER_SUFFIX, pluginVersion).with {
            pom.expectGetMissing()
            artifact.expectHeadMissing()
        }
    }

    @Override
    void expectPluginMarkerBroken(String pluginId, String pluginVersion) {
        module(pluginId, pluginId + PLUGIN_MARKER_SUFFIX, pluginVersion).with {
            pom.expectGetBroken()
        }
    }

    @Override
    void expectPluginMarkerQuery(String pluginId, String pluginVersion,
                                 @DelegatesTo(value = HttpServletResponse, strategy = Closure.DELEGATE_FIRST) Closure<?> markerQueryConfigurer) {
        def pluginMarker = module(pluginId, pluginId + PLUGIN_MARKER_SUFFIX, pluginVersion)
        server.expect(pluginMarker.pomPath, ["GET"], new HttpServer.ActionSupport("plugin marker pom") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                ConfigureUtil.configure(markerQueryConfigurer, response)
            }
        })
    }

    @Override
    void expectPluginResolution(String pluginId, String pluginVersion, String group, String artifactId, String version) {
        module(pluginId, pluginId + PLUGIN_MARKER_SUFFIX, pluginVersion).with {
            pom.expectGet()
            artifact.expectGet()
            allowAll()
        }
        module(group, artifactId, version).with {
            pom.expectGet()
            artifact.expectGet()
            allowAll()
        }
    }

    @Override
    void expectCachedPluginResolution(String pluginId, String pluginVersion, String group, String artifactId, String version) {
        module(pluginId, pluginId + PLUGIN_MARKER_SUFFIX, pluginVersion).with {
            pom.expectHead()
            artifact.expectHead()
            allowAll()
        }
        module(group, artifactId, version).with {
            pom.expectHead()
            artifact.expectHead()
            allowAll()
        }
    }
}
