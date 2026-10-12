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

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.TestProjectLocator

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CompileStatic
trait WithExternalRepository {
    HttpServer server
    private ExecutorService executor

    File getRepoDir() {
        new File(TestProjectLocator.findProjectDir(runner.testProject), 'repository')
    }

    abstract CrossVersionPerformanceTestRunner getRunner()

    HttpHandler createHandler(File repoDir) {
        return new StaticFileHandler(repoDir)
    }

    void startServer() {
        File dir = repoDir
        if (!dir.directory) {
            server = null
            return
        }
        server = HttpServer.create(new InetSocketAddress(0), 0)
        executor = Executors.newCachedThreadPool()
        server.setExecutor(executor)
        server.createContext("/", createHandler(dir))
        server.start()
    }

    void stopServer() {
        server?.stop(0)
        executor?.shutdownNow()
        executor = null
    }

    int getServerPort() {
        Objects.requireNonNull(server, "No server available to search for a local port")
        return server.address.port
    }
}
