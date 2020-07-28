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

import com.google.common.io.Files;
import net.rubygrapefruit.platform.SystemInfo;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class AdoptOpenJdkRemoteBinary {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdoptOpenJdkRemoteBinary.class);

    private final SystemInfo systemInfo;
    private final OperatingSystem operatingSystem;

    @Inject
    public AdoptOpenJdkRemoteBinary(SystemInfo systemInfo, OperatingSystem operatingSystem) {
        this.systemInfo = systemInfo;
        this.operatingSystem = operatingSystem;
    }

    // TODO: [bm] this might be downloading the same jdk multiple times in parallel builds
    // TODO: [bm] Alternative is using RepositoryTransportFactory+ExternalResourceAccessor+ChecksumService
    // Needs to be tied up with a FileStore impl as well
    // Adds risk as AdoptOpenJDK is hosted on S3 which does not allow HEAD requests; they
    // currently have a workaround to support HEAD requests for the "Gradle" User agent
    // Not using accessor might help us to move all of this into jvmServices for TD reuse
    // TODO: [bm] Need to handle if Adopt does not offer a matching jdk
    public Optional<File> download(JavaToolchainSpec spec) {
        File tmpFile = new File(Files.createTempDir(), toFilename(spec));
        int defaultTimeout = (int) TimeUnit.MINUTES.toMillis(5);
        URL source = toDownloadUrl(spec);
        LOGGER.info("Downloading {} to {}", source, tmpFile);
        try {
            FileUtils.copyURLToFile(source, tmpFile, defaultTimeout, defaultTimeout);
        } catch (IOException e) {
            throw new GradleException("Could not download JDK from " + source, e);
        }
        return Optional.of(tmpFile);
    }

    private URL toDownloadUrl(JavaToolchainSpec spec) {
        try {
            return constructUri(spec).toURL();
        } catch (MalformedURLException e) {
            throw new GradleException("Could not construct URL to download JDK for given toolchain requirements: " + spec.getDisplayName(), e);
        }
    }

    private URI constructUri(JavaToolchainSpec spec) {
        return URI.create(getServerBaseUri() +
            "v3/binary/latest/" + getMajorVersion(spec) +
            "/" +
            determineReleaseState() +
            "/" +
            determineOs() +
            "/" +
            determineArch() +
            "/jdk/hotspot/normal/adoptopenjdk");
    }

    private String toFilename(JavaToolchainSpec spec) {
        return String.format("adoptopenjdk-%s-%s-%s.%s", getMajorVersion(spec), determineArch(), determineOs(), determineFileExtension());
    }

    private String determineFileExtension() {
        if (operatingSystem.isWindows()) {
            return "zip";
        }
        return "tar.gz";
    }

    private String getMajorVersion(JavaToolchainSpec spec) {
        return spec.getLanguageVersion().get().getMajorVersion();
    }

    private String determineArch() {
        switch (systemInfo.getArchitecture()) {
            case i386:
                return "x32";
            case amd64:
                return "x64";
            case aarch64:
                return "aarch64";
        }
        return systemInfo.getArchitectureName();
    }

    private String determineOs() {
        if (operatingSystem.isWindows()) {
            return "windows";
        } else if (operatingSystem.isMacOsX()) {
            return "mac";
        } else if (operatingSystem.isLinux()) {
            return "linux";
        }
        return operatingSystem.getFamilyName();
    }

    // TODO: [bm] determine using API?
    // https://github.com/AdoptOpenJDK/openjdk-api-v3/issues/256
    private String determineReleaseState() {
        return "ga";
    }

    private String getServerBaseUri() {
        String baseUri = System.getProperty("org.gradle.jvm.toolchain.install.internal.adoptopenjdk.baseUri", "https://api.adoptopenjdk.net/");
        if (!baseUri.endsWith("/")) {
            baseUri += "/";
        }
        return baseUri;
    }

}
