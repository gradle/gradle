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

package org.gradle.jvm.toolchain.internal.install;

import org.gradle.internal.FileUtils;
import org.gradle.internal.SafeFileLocationUtils;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.LocalBinaryResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainDownloadException;
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;

@ServiceScope({Scope.Build.class})
public interface JavaToolchainProvisioningService {

    File tryInstall(JavaToolchainSpec spec) throws ToolchainDownloadException, ToolchainProvisioningException;

    default boolean isAutoDownloadEnabled() {
        return true;
    }

    default boolean hasConfiguredToolchainRepositories() {
        return true;
    }

    /**
     * Returns the file name provided for the resource, or {@code null} if none was provided.
     */
    default @Nullable String getFileName(URI uri, ExternalResource resource) {
        if (resource instanceof LocalBinaryResource) {
            String fileName = ((LocalBinaryResource) resource).getBaseName();
            if (fileName != null) {
                return fileName;
            }
        }
        ExternalResourceMetaData metaData = resource.getMetaData();
        if (metaData == null) {
            throw ResourceExceptions.getMissing(uri);
        }
        String fileName = metaData.getFilename();
        return fileName == null || fileName.isEmpty() ? null : fileName;
    }

    /**
     * Builds the local file name for a downloaded toolchain archive.
     * <p>
     * Uses the file name provided for the resource. If there is none, for example when the server sends
     * {@code Content-Disposition: attachment} without a filename, the name is derived from a hash of the URI.
     * The archive format is detected from the file content when unpacking.
     */
    default String buildFileNameWithDetails(URI uri, ExternalResource resource, JavaToolchainSpec spec) {
        String fileName = getFileName(uri, resource);
        if (fileName == null) {
            fileName = "jdk-" + Hashing.hashString(uri.toString()).toCompactString();
        }
        String id = "-" + spec.getVendor().get() + '-' + spec.getLanguageVersion().get().asInt();
        String nameWithIdUnsafe = FileUtils.addSuffixToName(fileName, id);
        return SafeFileLocationUtils.toSafeFileName(nameWithIdUnsafe, false);
    }
}
