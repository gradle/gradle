/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.services;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.classpath.ClasspathWalker;
import org.gradle.internal.classpath.InPlaceClasspathBuilder;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactory;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForAgent;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.inject.Inject;

public abstract class InjectedInstrumentationServices {
    private final Lazy<ClasspathElementTransformFactoryForAgent> transformFactory = Lazy.locking().of(
        () -> new ClasspathElementTransformFactoryForAgent(new InPlaceClasspathBuilder(), getClasspathWalker())
    );
    private final Lazy<ClasspathElementTransformFactoryForLegacy> legacyTransformFactory = Lazy.locking().of(
        () -> new ClasspathElementTransformFactoryForLegacy(new InPlaceClasspathBuilder(), getClasspathWalker())
    );

    public InjectedInstrumentationServices() {
        // Needed to generate a class
    }

    @Inject
    public abstract ClasspathWalker getClasspathWalker();

    @Inject
    public abstract FileSystemAccess getFileSystemAccess();

    @Inject
    public abstract GradleCoreInstrumentationTypeRegistry getGradleCoreInstrumentationTypeRegistry();

    @Inject
    public abstract GlobalCacheLocations getGlobalCacheLocations();

    @Inject
    public abstract StringInterner getStringInterner();

    public ClasspathElementTransformFactory getTransformFactory(boolean isAgentSupported) {
        return isAgentSupported ? transformFactory.get() : legacyTransformFactory.get();
    }
}
