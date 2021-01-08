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

package org.gradle.nativeplatform.test.xctest.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.nativeplatform.tasks.LinkMachOBundle;
import org.gradle.nativeplatform.test.xctest.SwiftXCTestBundle;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultSwiftXCTestBundle extends DefaultSwiftXCTestBinary implements SwiftXCTestBundle {
    private final Property<LinkMachOBundle> linkTask;

    @Inject
    public DefaultSwiftXCTestBundle(Names names, ObjectFactory objectFactory, Provider<String> module, boolean testable, FileCollection source, ConfigurationContainer configurations, Configuration implementation, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider, NativeVariantIdentity identity) {
        super(names, objectFactory, module, testable, source, configurations, implementation, targetPlatform, toolChain, platformToolProvider, identity);
        linkTask = objectFactory.property(LinkMachOBundle.class);
    }

    @Override
    public Property<LinkMachOBundle> getLinkTask() {
        return linkTask;
    }
}
