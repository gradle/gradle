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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.internal.externalresource.DefaultExternalResourceMetaData;
import org.gradle.api.internal.externalresource.ExternalResourceMetaData;

import java.util.Date;

public class ArtifactOriginWithMetaData extends ArtifactOrigin {

    private final ExternalResourceMetaData metaData;

    public ArtifactOriginWithMetaData(Artifact artifact, boolean isLocal, String location, long lastModified, long contentLength) {
        this(artifact, isLocal, location, lastModified > 0 ? new Date(lastModified) : null, contentLength);
    }

    public ArtifactOriginWithMetaData(Artifact artifact, boolean isLocal, String location, Date lastModified, long contentLength) {
        super(artifact, isLocal, location);
        metaData = new DefaultExternalResourceMetaData(location, lastModified, contentLength);
    }

    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }
}
