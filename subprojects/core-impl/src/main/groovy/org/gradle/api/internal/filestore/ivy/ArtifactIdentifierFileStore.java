/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.filestore.ivy;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.filestore.GroupedAndNamedUniqueFileStore;
import org.gradle.api.internal.filestore.PathKeyFileStore;

public class ArtifactIdentifierFileStore extends GroupedAndNamedUniqueFileStore<ModuleVersionArtifactMetaData> {

    private static final String GROUP_PATTERN = "[organisation]/[module](/[branch])/[revision]";
    private static final String NAME_PATTERN = "[artifact]-[revision](-[classifier])(.[ext])";

    public ArtifactIdentifierFileStore(PathKeyFileStore pathKeyFileStore, TemporaryFileProvider temporaryFileProvider) {
        super(pathKeyFileStore, temporaryFileProvider, toTransformer(GROUP_PATTERN), toTransformer(NAME_PATTERN));
    }

    private static Transformer<String, ModuleVersionArtifactMetaData> toTransformer(final String pattern) {
        final ResourcePattern resourcePattern = new IvyResourcePattern(pattern);
        return new Transformer<String, ModuleVersionArtifactMetaData>() {
             public String transform(ModuleVersionArtifactMetaData artifact) {
                 return resourcePattern.toPath(artifact);
             }
         };
    }
}
