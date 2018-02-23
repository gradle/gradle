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
import groovy.transform.SelfType
import org.apache.mina.util.AvailablePortFinder
import org.gradle.performance.fixture.TestProjectLocator
import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet.Context
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.resource.Resource

@CompileStatic
@SelfType(AbstractCrossVersionPerformanceTest)
trait WithExternalRepository {
    Server server
    int serverPort

    File getRepoDir() {
        new File(new TestProjectLocator().findProjectDir(runner.testProject), 'repository')
    }

    Context createContext() {
        def context = new WebAppContext()
        context.setContextPath("/")
        context.setBaseResource(Resource.newResource(repoDir.getAbsolutePath()))
        context
    }

    void startServer() {
        try {
            serverPort = AvailablePortFinder.getNextAvailable(5000)
            server = new Server(serverPort)
            Context context = createContext()
            context.setContextPath("/")
            context.setBaseResource(Resource.newResource(repoDir.getAbsolutePath()))
            server.addHandler(context)
            server.start()
        } catch (IllegalArgumentException ex) {
            server = null // repository not found, probably running on coordinator. If not, error will be caught later
        }
    }

    void stopServer() {
        server?.stop()
    }
}
