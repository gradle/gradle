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

package org.gradle.test.fixtures.resource

interface RemoteResource {

    URI getUri()

    void expectDownload()

    void expectDownloadMissing()

    void expectDownloadBroken()

    void expectMetadataRetrieve()

    void expectMetadataRetrieveMissing()

    void expectMetadataRetrieveBroken()

    /**
     * Expects that the parent directories of this resource be created, for those resources where this is required. May be a no-op.
     */
    void expectParentMkdir()

    /**
     * Expects that the parent directories of this resource be checked, for those resources where this is required. May be a no-op.
     */
    void expectParentCheckdir()

    void expectUpload()

    void expectUploadBroken()
}
