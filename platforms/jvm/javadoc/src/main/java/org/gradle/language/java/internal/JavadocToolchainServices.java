/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java.internal;

import org.gradle.api.tasks.javadoc.internal.JavadocToolAdapter;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.jvm.toolchain.internal.ToolchainToolFactory;
import org.gradle.process.internal.ExecActionFactory;

/**
 * Provides toolchain services related to Javadoc.
 * <p>
 * This service is responsible for creating {@link ToolchainToolFactory} instances that can
 * create {@link JavadocTool} instances.
 */
public class JavadocToolchainServices extends AbstractGradleModuleServices {
    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new JavaProjectScopeServices());
    }

    private static class JavaProjectScopeServices implements ServiceRegistrationProvider {
        @Provides
        public ToolchainToolFactory createToolFactory(ExecActionFactory generator) {
            // TODO should we create all tools via this factory?
            return new ToolchainToolFactory() {
                @Override
                public <T> T create(Class<T> toolType, JavaToolchain toolchain) {
                    if (toolType == JavadocTool.class) {
                        return toolType.cast(new JavadocToolAdapter(generator, toolchain));
                    }
                    return null;
                }
            };
        }
    }
}
