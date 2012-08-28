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

package org.gradle.integtests.resolve.http

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

abstract class AbstractHttpsRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule TestResources resources
    File clientStore
    File serverStore

    abstract protected String setupRepo()

    def "resolve with server authentication"() {
        setupCertStores()
        server.enableSsl(serverStore.path, "asdfgh")
        server.start()

        println "SSL port: $server.sslPort"

        def repoType = setupRepo()
        setupBuildFile(repoType)

        when:
        executer.withArgument("-Djavax.net.ssl.trustStore=$serverStore.path")
                .withArgument("-Djavax.net.ssl.trustStorePassword=asdfgh")
                .withTasks('libs').run()

        then:
        file('libs').assertHasDescendants('my-module-1.0.jar')
    }

    def "resolve with server and client authentication"() {
        setupCertStores()
        server.enableSsl(serverStore.path, "asdfgh", clientStore.path, "asdfgh")
        server.start()

        def repoType = setupRepo()
        setupBuildFile(repoType)

        when:
        executer.withArgument("-Djavax.net.ssl.trustStore=$serverStore.path")
                .withArgument("-Djavax.net.ssl.trustStorePassword=asdfgh")
                .withArgument("-Djavax.net.ssl.keyStore=$clientStore.path")
                .withArgument("-Djavax.net.ssl.keyStorePassword=asdfgh")
                .withTasks('libs').run()

        then:
        file('libs').assertHasDescendants('my-module-1.0.jar')
    }

    def setupCertStores() {
        clientStore = resources.dir.file("clientStore")
        serverStore = resources.dir.file("serverStore")
    }

    private void setupBuildFile(String repoType) {
        buildFile << """
repositories {
    $repoType { url 'https://localhost:${server.sslPort}/repo1' }
}
configurations { compile }
dependencies {
    compile 'my-group:my-module:1.0'
}
task libs(type: Copy) {
    into 'libs'
    from configurations.compile
}
        """
    }
}

