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

package org.gradle.internal.resource.transport.gcp.gcs;

import com.google.api.services.storage.model.StorageObject;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.net.URI;

final class ResourceMapper {

    static ExternalResourceMetaData toExternalResourceMetaData(URI uri, StorageObject storageObject) {
        return new DefaultExternalResourceMetaData(
            uri,
            storageObject.getUpdated().getValue(),
            storageObject.getSize().longValue(),
            storageObject.getContentType(),
            storageObject.getEtag(),
            null // we cannot use md5 instead of sha1 here because cache will get corrupted due to its expectation of sha1 hashes
        );
    }

    private ResourceMapper() {
        throw new AssertionError("No instances");
    }
}
