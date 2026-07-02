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

@CompileStatic
class SslConfiguration {
    private List<String> includeProtocols = ["TLSv1.2"]
    private final List<String> excludeProtocols = []

    void setIncludeProtocols(String... protocols) {
        this.includeProtocols = protocols.toList()
    }

    List<String> getIncludeProtocols() {
        return includeProtocols
    }

    void addExcludeProtocols(String... protocols) {
        this.excludeProtocols.addAll(protocols)
    }

    List<String> getExcludeProtocols() {
        return excludeProtocols
    }

    List<String> getEffectiveProtocols() {
        List<String> effective = new ArrayList<>(includeProtocols)
        effective.removeAll(excludeProtocols)
        return effective
    }
}
