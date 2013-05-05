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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.filestore.GroupedAndNamedUniqueFileStore;
import org.gradle.api.internal.filestore.PathKeyFileStore;

public class ArtifactRevisionIdFileStore extends GroupedAndNamedUniqueFileStore<ArtifactRevisionId> {

    private static final String GROUP_PATTERN = "[organisation]/[module](/[branch])/[revision]/[type]";
    private static final String NAME_PATTERN = "[artifact]-[revision](-[classifier])(.[ext])";

    public ArtifactRevisionIdFileStore(PathKeyFileStore pathKeyFileStore, TemporaryFileProvider temporaryFileProvider) {
        super(pathKeyFileStore, temporaryFileProvider, toTransformer(GROUP_PATTERN), toTransformer(NAME_PATTERN));
    }

    private static Transformer<String, ArtifactRevisionId> toTransformer(final String pattern) {
        return new Transformer<String, ArtifactRevisionId>() {
            public String transform(ArtifactRevisionId id) {
                String substitute = pattern;
                substitute = IvyPatternHelper.substituteToken(substitute, "organisation", id.getModuleRevisionId().getOrganisation().replace('/', '.'));
                return IvyPatternHelper.substitute(substitute, new DefaultArtifact(id, null, null, false));
            }
        };
    }

}
