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

package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.file.TestFile

trait MavenRemoteModule {
    abstract MavenRemoteResource getPomFile()

    abstract MavenRemoteResource getArtifactFile(String type)

    abstract MavenRemoteResource getMetaData()

    abstract MavenRemoteResource getRootMetaData()

    abstract MavenRemoteResource getJar()

    abstract MavenRemoteResource getJavaSource()

    abstract MavenRemoteResource getJavadoc()

    abstract TestFile getStorageDirectory()

    void expectFirstTimeSnapshotPublish() {
        metaData.expectDownload()
        jar.expectPublish()
        metaData.expectDownload()
        metaData.expectPublish()
        rootMetaData.expectDownload()
        rootMetaData.expectPublish()
        pomFile.expectPublish()
    }

    void expectSnapshotRePublish() {
        metaData.expectDownload()
        metaData.expectSha1Download()
        jar.expectPublish()
        metaData.expectDownload()
        metaData.expectSha1Download()
        metaData.expectPublish()
        rootMetaData.expectDownload()
        rootMetaData.expectSha1Download()
        rootMetaData.expectPublish()
        pomFile.expectPublish()
    }

    void expectReleasePublish() {
        jar.expectPublish()
        rootMetaData.expectDownload()
        rootMetaData.expectPublish()
        pomFile.expectPublish()
    }

}
