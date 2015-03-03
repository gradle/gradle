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

package org.gradle.integtests.tooling.fixture

import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.util.GradleVersion

class ExternalToolingApiDistribution implements ToolingApiDistribution {
    private final GradleVersion version
    private final Collection<File> classpath
    private final ClassLoader slf4jClassLoader

    ExternalToolingApiDistribution(String version, Collection<File> classpath, ClassLoader slf4jClassLoader) {
        this.version = GradleVersion.version(version)
        this.classpath = classpath
        this.slf4jClassLoader = slf4jClassLoader;
    }

    GradleVersion getVersion() {
        version
    }
    
    Collection<File> getClasspath() {
        classpath
    }
    
    ClassLoader getClassLoader() {
        new MutableURLClassLoader(slf4jClassLoader, classpath*.toURI()*.toURL())
    }
    
    String toString() {
        "Tooling API $version.version"
    }
}
