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

import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion2;

import java.io.File;
import java.util.*;

public class CachingToolingImplementationLoader implements ToolingImplementationLoader {
    private final ToolingImplementationLoader loader;
    private final Map<Set<File>, ConnectionFactoryVersion2> connections = new HashMap<Set<File>, ConnectionFactoryVersion2>();

    public CachingToolingImplementationLoader(ToolingImplementationLoader loader) {
        this.loader = loader;
    }

    public ConnectionFactoryVersion2 create(Distribution distribution) {
        Set<File> classpath = new LinkedHashSet<File>(distribution.getToolingImplementationClasspath());
        ConnectionFactoryVersion2 factory = connections.get(classpath);
        if (factory == null) {
            factory = loader.create(distribution);
            connections.put(classpath, factory);
        }

        return factory;
    }
}
