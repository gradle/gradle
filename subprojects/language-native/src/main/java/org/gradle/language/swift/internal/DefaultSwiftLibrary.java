/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.provider.LockableSetProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultSwiftLibrary extends DefaultSwiftComponent implements SwiftLibrary {
    private final Configuration api;
    private final ObjectFactory objectFactory;
    private final LockableSetProperty<Linkage> linkage;
    private final ConfigurationContainer configurations;
    private final Property<SwiftBinary> developmentBinary;

    @Inject
    public DefaultSwiftLibrary(String name, ObjectFactory objectFactory, FileOperations fileOperations, ConfigurationContainer configurations) {
        super(name, fileOperations, objectFactory, configurations);
        this.objectFactory = objectFactory;
        this.configurations = configurations;
        this.developmentBinary = objectFactory.property(SwiftBinary.class);

        linkage = new LockableSetProperty<Linkage>(objectFactory.setProperty(Linkage.class));
        linkage.add(Linkage.SHARED);

        api = configurations.create(getNames().withSuffix("api"));
        api.setCanBeConsumed(false);
        api.setCanBeResolved(false);
        getImplementationDependencies().extendsFrom(api);
    }

    public SwiftStaticLibrary addStaticLibrary(String nameSuffix, boolean debuggable, boolean optimized, boolean testable, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        SwiftStaticLibrary result = objectFactory.newInstance(DefaultSwiftStaticLibrary.class, getName() + StringUtils.capitalize(nameSuffix), getModule(), debuggable, optimized, testable, getSwiftSource(), getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider);
        getBinaries().add(result);
        return result;
    }

    public SwiftSharedLibrary addSharedLibrary(String nameSuffix, boolean debuggable, boolean optimized, boolean testable, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        SwiftSharedLibrary result = objectFactory.newInstance(DefaultSwiftSharedLibrary.class, getName() + StringUtils.capitalize(nameSuffix), getModule(), debuggable, optimized, testable, getSwiftSource(), configurations, getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider);
        getBinaries().add(result);
        return result;
    }

    @Override
    public Configuration getApiDependencies() {
        return api;
    }

    @Override
    public Property<SwiftBinary> getDevelopmentBinary() {
        return developmentBinary;
    }

    @Override
    public LockableSetProperty<Linkage> getLinkage() {
        return linkage;
    }
}
