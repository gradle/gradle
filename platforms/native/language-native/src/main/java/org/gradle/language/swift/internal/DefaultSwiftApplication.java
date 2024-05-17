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

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.language.ComponentDependencies;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.DefaultComponentDependencies;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;

public class DefaultSwiftApplication extends DefaultSwiftComponent<SwiftBinary> implements SwiftApplication {
    private final ObjectFactory objectFactory;
    private final Property<SwiftExecutable> developmentBinary;
    private final DefaultComponentDependencies dependencies;

    @Inject
    public DefaultSwiftApplication(String name, ObjectFactory objectFactory) {
        super(name, objectFactory);
        this.objectFactory = objectFactory;
        this.developmentBinary = objectFactory.property(SwiftExecutable.class);
        this.dependencies = objectFactory.newInstance(DefaultComponentDependencies.class, getNames().withSuffix("implementation"));
    }

    @Override
    public DisplayName getDisplayName() {
        return Describables.withTypeAndName("Swift application", getName());
    }

    @Override
    public Configuration getImplementationDependencies() {
        return dependencies.getImplementationDependencies();
    }

    @Override
    public ComponentDependencies getDependencies() {
        return dependencies;
    }

    public void dependencies(Action<? super ComponentDependencies> action) {
        action.execute(dependencies);
    }

    public SwiftExecutable addExecutable(NativeVariantIdentity identity, boolean testable, SwiftPlatform targetPlatform, NativeToolChainInternal toolChain, PlatformToolProvider platformToolProvider) {
        SwiftExecutable result = objectFactory.newInstance(DefaultSwiftExecutable.class, getNames().append(identity.getName()), getModule(), testable, getSwiftSource(), getImplementationDependencies(), targetPlatform, toolChain, platformToolProvider, identity);
        getBinaries().add(result);
        return result;
    }

    @Override
    public Property<SwiftExecutable> getDevelopmentBinary() {
        return developmentBinary;
    }
}
