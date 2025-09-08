/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;

public class SoftwareFeatureSupportInternal {

    private static final String CONTEXT_EXTENSION_NAME = "$softwareFeatureContext";

    public interface SoftwareFeatureContext {
        SoftwareFeatureApplicator getSoftwareFeatureApplicator();

        SoftwareFeatureRegistry getSoftwareFeatureRegistry();
    }

    private static class DefaultSoftwareFeatureContext implements SoftwareFeatureContext {
        private final SoftwareFeatureApplicator softwareFeatureApplicator;
        private final SoftwareFeatureRegistry softwareFeatureRegistry;

        private DefaultSoftwareFeatureContext(SoftwareFeatureApplicator softwareFeatureApplicator, SoftwareFeatureRegistry softwareFeatureRegistry) {
            this.softwareFeatureApplicator = softwareFeatureApplicator;
            this.softwareFeatureRegistry = softwareFeatureRegistry;
        }

        @Override
        public SoftwareFeatureApplicator getSoftwareFeatureApplicator() {
            return softwareFeatureApplicator;
        }

        @Override
        public SoftwareFeatureRegistry getSoftwareFeatureRegistry() {
            return softwareFeatureRegistry;
        }
    }

    public static SoftwareFeatureContext getContext(ExtensionAware target) {
        return (SoftwareFeatureContext) target.getExtensions().getByName(CONTEXT_EXTENSION_NAME);
    }

    public static void registerContextIfAbsent(
        ExtensionAware target,
        SoftwareFeatureApplicator softwareFeatureApplicator,
        SoftwareFeatureRegistry softwareFeatureRegistry
    ) {
        ExtensionContainer extensions = target.getExtensions();
        if (extensions.findByName(CONTEXT_EXTENSION_NAME) == null) {
            extensions.add(CONTEXT_EXTENSION_NAME, new DefaultSoftwareFeatureContext(softwareFeatureApplicator, softwareFeatureRegistry));
        }
    }

}
