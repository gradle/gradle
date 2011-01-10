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

import org.gradle.api.internal.DefaultClassPathProvider;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DistributionFactory {
    public Distribution getCurrentDistribution() {
        return new Distribution() {
            public Set<File> getToolingImplementationClasspath() {
                DefaultClassPathProvider provider = new DefaultClassPathProvider();
                return provider.findClassPath("GRADLE_RUNTIME");
            }
        };
    }

    public Distribution getDistribution(final File gradleHomeDir) {
        return new Distribution() {
            public Set<File> getToolingImplementationClasspath() {
                Set<File> files = new LinkedHashSet<File>();
                File libDir = new File(gradleHomeDir, "lib");
                for (File file : libDir.listFiles()) {
                    if (file.getName().endsWith(".jar")) {
                        files.add(file);
                    }
                }
                return files;
            }
        };
    }
}
