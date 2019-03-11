/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.AbstractCompile;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;

/**
 * Helpers for Java plugins. They are in a separate class so that they don't leak
 * into the public API.
 */
public class JavaPluginsHelper {
    public static void registerClassesDirVariant(final Provider<? extends AbstractCompile> compileTask, ObjectFactory objectFactory, Configuration configuration) {
        // Define a classes variant to use for compilation
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariant variant = publications.getVariants().maybeCreate("classes");
        variant.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API_CLASSES));
        variant.artifact(new IntermediateJavaArtifact(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, compileTask) {
            @Override
            public File getFile() {
                return compileTask.get().getDestinationDir();
            }
        });
    }

    /**
     * A custom artifact type which allows the getFile call to be done lazily only when the
     * artifact is actually needed.
     */
    public abstract static class IntermediateJavaArtifact extends AbstractPublishArtifact {
        private final String type;

        public IntermediateJavaArtifact(String type, Object task) {
            super(task);
            this.type = type;
        }

        @Override
        public String getName() {
            return getFile().getName();
        }

        @Override
        public String getExtension() {
            return "";
        }

        @Override
        public String getType() {
            return type;
        }

        @Nullable
        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public Date getDate() {
            return null;
        }
    }
}
