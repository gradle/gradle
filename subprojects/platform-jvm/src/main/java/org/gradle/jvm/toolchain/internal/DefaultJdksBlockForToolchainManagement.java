/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRegistry;
import org.gradle.jvm.toolchain.JdksBlockForToolchainManagement;

public abstract class DefaultJdksBlockForToolchainManagement implements JdksBlockForToolchainManagement, JavaToolchainRepositoryRegistrationListener, Stoppable {

    private final JavaToolchainRepositoryRegistryInternal registry;

    private final ListenerManager listenerManager;

    public DefaultJdksBlockForToolchainManagement(JavaToolchainRepositoryRegistry registry, ListenerManager listenerManager) {
        this.registry = (JavaToolchainRepositoryRegistryInternal) registry;
        this.listenerManager = listenerManager;
        listenerManager.addListener(this);
    }

    @Override
    public void add(String registrationName) {
        registry.request(registrationName);
    }

    @Override
    public void add(JavaToolchainRepositoryRegistration registration) {
        registry.request(registration);
    }

    @Override
    public void onRegister(JavaToolchainRepositoryRegistrationInternal registration) {
        getExtensions().add(JavaToolchainRepositoryRegistration.class, registration.getName(), registration);
    }

    @Override
    public void stop() {
        listenerManager.removeListener(this); //TODO (#21082): is this being called now, after latest changes?
    }

}
