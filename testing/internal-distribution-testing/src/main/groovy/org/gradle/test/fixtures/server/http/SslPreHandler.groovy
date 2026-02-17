/*
 * Copyright 2026 the original author or authors.
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

import groovy.transform.CompileStatic
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.Request

import java.util.function.Consumer

@CompileStatic
class SslPreHandler implements HttpConfiguration.Customizer {

    private final List<Consumer<Request>> consumers = []

    void registerCustomizer(Consumer<Request> consumer) {
        consumers << consumer
    }

    @Override
    void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
        consumers.each {
            it.accept(request)
        }
    }
}
