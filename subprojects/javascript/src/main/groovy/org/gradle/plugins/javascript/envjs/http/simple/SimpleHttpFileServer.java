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

package org.gradle.plugins.javascript.envjs.http.simple;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.plugins.javascript.envjs.http.HttpFileServer;

import java.io.File;

public class SimpleHttpFileServer implements HttpFileServer {

    private final File contentRoot;
    private final Stoppable stopper;
    private int port;

    public SimpleHttpFileServer(File contentRoot, int port, Stoppable stopper) {
        this.contentRoot = contentRoot;
        this.stopper = stopper;
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public String getResourceUrl(String path) {
        return String.format("http://localhost:%s/%s", port, path.startsWith("/") ? path.substring(1) : path);
    }

    public File getContentRoot() {
        return contentRoot;
    }

    public void stop() {
        stopper.stop();
    }
}
