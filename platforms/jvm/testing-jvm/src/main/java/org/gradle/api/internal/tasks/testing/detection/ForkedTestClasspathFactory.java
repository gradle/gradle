/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.worker.ForkedTestClasspath;
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Constructs the application and implementation classpaths for a test process.
 *
 * @see ForkedTestClasspath
 */
public class ForkedTestClasspathFactory {

    private final ModuleRegistry moduleRegistry;

    public ForkedTestClasspathFactory(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public ForkedTestClasspath create(
        Iterable<? extends File> classpath,
        Iterable<? extends File> modulepath
    ) {
        return new ForkedTestClasspath(
            ImmutableList.copyOf(classpath),
            ImmutableList.copyOf(modulepath),
            withImplementation(ImmutableList.of())
        );
    }

    /**
     * Constructs the implementation classpath required by the Gradle testing infrastructure
     * while also mixing-in the additional implementation classpath jars required by the
     */
    private ImmutableList<URL> withImplementation(List<URL> additionalImplementationClasspath) {
        return ImmutableList.copyOf(CollectionUtils.flattenCollections(URL.class,
            moduleRegistry.getModule("gradle-classloaders").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-worker-main").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-logging-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-messaging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-files").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-file-temp").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-hashing").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-base-asm").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-base-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-stdlib-java-extensions").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-logging").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-enterprise-workers").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-cli").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-concurrent").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-io").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-native").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-serialization").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-service-lookup").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-service-provider").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-service-registry-builder").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-service-registry-impl").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-collections").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-time").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-base-infrastructure").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-testing-jvm-infrastructure").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-process-memory-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-process-services").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-build-operations").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getModule("gradle-problems-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("slf4j-api").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("jul-to-slf4j").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("native-platform").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("kryo").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("commons-lang3").getImplementationClasspath().getAsURLs(),
            moduleRegistry.getExternalModule("javax.inject").getImplementationClasspath().getAsURLs(),
            additionalImplementationClasspath
        ));
    }

}
