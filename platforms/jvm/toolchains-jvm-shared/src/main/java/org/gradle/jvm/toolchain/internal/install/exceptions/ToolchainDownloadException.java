/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.install.exceptions;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Arrays;

@Contextual
public class ToolchainDownloadException extends GradleException {

    @SuppressWarnings("this-escape")
    public ToolchainDownloadException(JavaToolchainSpec spec, String url, @Nullable String cause) {
        super(getMessage(spec, url, cause), Arrays.asList(ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION, ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION));
    }

    public ToolchainDownloadException(JavaToolchainSpec spec, URI uri, Throwable cause) {
        super(getMessage(spec, uri.toString(), cause.getMessage()), cause);
    }

    @NonNull
    private static String getMessage(JavaToolchainSpec spec, String url, @Nullable String cause) {
        return "Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from '" + url + "'" + (cause != null && !cause.isEmpty() ? ", due to: " + cause : ".");
    }
}
