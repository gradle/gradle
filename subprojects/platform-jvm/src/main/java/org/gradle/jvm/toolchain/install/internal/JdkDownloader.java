/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal;

import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.RemoteResourceService;

import java.io.File;
import java.net.URI;

public class JdkDownloader {

    private final RemoteResourceService remoteResourceService;

    public JdkDownloader(RemoteResourceService remoteResourceService) {
        this.remoteResourceService = remoteResourceService;
    }

    public void download(URI source, File tmpFile) {
        try {
            remoteResourceService.withResource(source, tmpFile.getName(), res -> res.persistInto(tmpFile));
        } catch (MissingResourceException e) {
            throw new MissingResourceException(source, "Unable to download toolchain. " +
                "This might indicate that the combination " +
                "(version, architecture, release/early access, ...) for the " +
                "requested JDK is not available.", e);
        }
    }

}
