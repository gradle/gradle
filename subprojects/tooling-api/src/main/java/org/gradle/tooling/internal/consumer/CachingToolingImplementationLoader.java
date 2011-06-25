/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.internal.protocol.ConnectionVersion4;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CachingToolingImplementationLoader implements ToolingImplementationLoader {
    private final ToolingImplementationLoader loader;
    private final Map<Set<File>, ConnectionVersion4> connections = new HashMap<Set<File>, ConnectionVersion4>();

    public CachingToolingImplementationLoader(ToolingImplementationLoader loader) {
        this.loader = loader;
    }

    public ConnectionVersion4 create(Distribution distribution) {
        Set<File> classpath = new LinkedHashSet<File>(distribution.getToolingImplementationClasspath());
        ConnectionVersion4 connection = connections.get(classpath);
        if (connection == null) {
            connection = loader.create(distribution);
            connections.put(classpath, connection);
        }

        return connection;
    }
}
