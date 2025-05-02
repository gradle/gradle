/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainDownload;

import java.net.URI;

/**
 * The response provided by a {@link JavaToolchainResolver} to a specific
 * {@link JavaToolchainRequest}.
 * <p>
 * Contains the download URI from which a Java toolchain matching the request
 * can be downloaded from. The URI must point to either a ZIP or a TAR archive
 * and has to be secure (so simple HTTP is not allowed, only HTTPS).
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainDownload {

    URI getUri();

    static JavaToolchainDownload fromUri(URI uri) {
        return DefaultJavaToolchainDownload.fromUri(uri);
    }

}
