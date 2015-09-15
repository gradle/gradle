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
package org.gradle.tooling.internal.consumer.loader;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.hash.Hasher;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.util.GFileUtils;

import java.io.Closeable;
import java.io.File;
import java.util.*;
import java.util.zip.Adler32;

public class CachingToolingImplementationLoader implements ToolingImplementationLoader, Closeable {
    private final ToolingImplementationLoader loader;
    private final Map<Long, ConsumerConnection> connections = new HashMap<Long, ConsumerConnection>();

    public CachingToolingImplementationLoader(ToolingImplementationLoader loader) {
        this.loader = loader;
    }

    public ConsumerConnection create(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
        ClassPath classpath = distribution.getToolingImplementationClasspath(progressLoggerFactory, connectionParameters.getGradleUserHomeDir(), cancellationToken);
        Long hash = createHash(classpath);
        ConsumerConnection connection = connections.get(hash);
        if (connection == null) {
            connection = loader.create(distribution, progressLoggerFactory, connectionParameters, cancellationToken);
            connections.put(hash, connection);
        }

        return connection;
    }

    private Long createHash(ClassPath classPath){
        List<String> visitedFilePaths = Lists.newLinkedList();
        Set<File> visitedDirs = Sets.newLinkedHashSet();
        List<File> cpFiles = classPath.getAsFiles();

        Adler32 checksum = new Adler32();
        hash(checksum, visitedFilePaths, visitedDirs, cpFiles.iterator());
        return checksum.getValue();
    }

    /**
     * Copied logic here from HashClassPathSnapshotter but without the caching of filesnapshots boilerplate
     * */
    private void hash(Adler32 combinedHash, List<String> visitedFilePaths, Set<File> visitedDirs, Iterator<File> toHash) {
        Hasher hasher = new DefaultHasher();
        while (toHash.hasNext()) {
            File file = toHash.next();
            file = GFileUtils.canonicalise(file);
            if (file.isDirectory()) {
                if (visitedDirs.add(file)) {
                    hash(combinedHash, visitedFilePaths, visitedDirs, Iterators.forArray(file.listFiles()));
                }
            } else if (file.isFile()) {
                visitedFilePaths.add(file.getAbsolutePath());
                combinedHash.update(hasher.hash(file));
            }
        }
    }

    public void close() {
        try {
            CompositeStoppable.stoppable(connections.values()).stop();
        } finally {
            connections.clear();
        }
    }
}
