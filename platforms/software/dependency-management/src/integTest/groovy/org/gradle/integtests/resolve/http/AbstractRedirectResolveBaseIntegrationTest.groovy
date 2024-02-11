/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.http

import org.gradle.api.logging.configuration.WarningMode
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.SetSystemProperties
import org.junit.Rule

abstract class AbstractRedirectResolveBaseIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule HttpServer backingServer = new HttpServer()

    abstract String getFrontServerBaseUrl();

    abstract boolean defaultAllowInsecureProtocol();

    def module = ivyRepo().module('group', 'projectA').publish()

    abstract void beforeServerStart();

    def setupServer() {
        beforeServerStart()
        server.useHostname()
        backingServer.useHostname()
        backingServer.start()
    }

    @Override
    def setup() {
        setupServer()
        executer.withWarningMode(WarningMode.All)
    }

    def configurationWithIvyDependencyAndExpectedArtifact(String dependency, String expectedArtifact, boolean allowInsecureProtocol = defaultAllowInsecureProtocol()) {
        String allowInsecureProtocolCall =  allowInsecureProtocol ? "allowInsecureProtocol true" : ""
        """
            repositories {
                ivy {
                    url "$frontServerBaseUrl/repo"
                    metadataSources {
                        ivyDescriptor()
                        artifact()
                    }
                    $allowInsecureProtocolCall
                }
            }
            configurations { compile }
            dependencies { compile '$dependency' }
            task listJars {
                def files = configurations.compile
                doLast {
                    assert files*.name == ['$expectedArtifact']
                }
            }
        """
    }
}
