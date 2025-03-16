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

package org.gradle.performance

import groovy.transform.CompileStatic
import org.apache.mina.util.AvailablePortFinder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.TestProjectLocator

@CompileStatic
trait WithExternalRepository {
    Server server
    int serverPort

    File getRepoDir() {
        new File(TestProjectLocator.findProjectDir(runner.testProject), 'repository')
    }

    abstract CrossVersionPerformanceTestRunner getRunner()

    WebAppContext createContext() {
        def context = new WebAppContext()
        context.setContextPath("/")
        context.setBaseResource(Resource.newResource(repoDir.getAbsolutePath()))
        context
    }

    void startServer() {
        try {
            serverPort = AvailablePortFinder.getNextAvailable(5000)
            server = new Server(serverPort)
            WebAppContext context = createContext()
            context.setContextPath("/")
            context.setBaseResource(Resource.newResource(repoDir.getAbsolutePath()))
            server.insertHandler(context)
            server.start()
        } catch (IllegalArgumentException ignored) {
            server = null // repository not found, probably running on coordinator. If not, error will be caught later
        }
    }

    void stopServer() {
        server?.stop()
    }
}
