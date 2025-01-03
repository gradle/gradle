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
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Contextual
public class ToolchainDownloadException extends GradleException implements ResolutionProvider {

    private final List<String> resolutions;

    public ToolchainDownloadException(JavaToolchainSpec spec, String url, @Nullable String cause, String... resolutions) {
        super("Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from '" + url + "'" + (cause != null && !cause.isEmpty()  ? ", due to: " + cause : "."));
        this.resolutions = Arrays.asList(resolutions);
    }

    public ToolchainDownloadException(JavaToolchainSpec spec, String url, @Nullable Throwable cause) {
        this(spec, url, cause != null ? cause.getMessage() : null);
    }

    public ToolchainDownloadException(JavaToolchainSpec spec, URI uri, @Nullable Throwable cause) {
        this(spec, uri.toString(), cause != null ? cause.getMessage() : null);
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
