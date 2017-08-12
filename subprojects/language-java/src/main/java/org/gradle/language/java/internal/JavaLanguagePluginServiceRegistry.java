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

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.compile.AnnotationProcessorDetector;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.java.artifact.JavadocArtifact;

public class JavaLanguagePluginServiceRegistry extends AbstractPluginServiceRegistry {
    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.addProvider(new JavaGradleScopeServices());
    }

    private static class JavaGradleScopeServices {
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary.class)
                    .registerArtifactType(JavadocArtifact.class, ArtifactType.JAVADOC);
        }

        public AnnotationProcessorDetector createAnnotationProcessorDetector(FileCollectionFactory fileCollectionFactory, FileContentCacheFactory cacheFactory) {
            return new AnnotationProcessorDetector(fileCollectionFactory, cacheFactory);
        }
    }
}
