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

import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Optional;

public class AdoptOpenJdkRemoteBinary {

    private final SystemInfo systemInfo;
    private final OperatingSystem operatingSystem;
    private final AdoptOpenJdkDownloader downloader;
    private final Provider<String> rootUrl;

    @Inject
    public AdoptOpenJdkRemoteBinary(SystemInfo systemInfo, OperatingSystem operatingSystem, AdoptOpenJdkDownloader downloader, ProviderFactory providerFactory) {
        this.systemInfo = systemInfo;
        this.operatingSystem = operatingSystem;
        this.downloader = downloader;
        rootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri").forUseAtConfigurationTime();
    }

    public Optional<File> download(JavaToolchainSpec spec, File destinationFile) {
        if (!canProvideMatchingJdk(spec)) {
            return Optional.empty();
        }
        URI source = toDownloadUri(spec);
        downloader.download(source, destinationFile);
        return Optional.of(destinationFile);
    }

    public boolean canProvideMatchingJdk(JavaToolchainSpec spec) {
        final boolean matchesLanguageVersion = getLanguageVersion(spec).canCompileOrRun(8);
        final DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        JvmVendor vendor = JvmVendor.fromString("adoptopenjdk");
        boolean matchesVendor = vendorSpec == DefaultJvmVendorSpec.any() || vendorSpec.test(vendor);
        return matchesLanguageVersion && matchesVendor;
    }

    URI toDownloadUri(JavaToolchainSpec spec) {
        return constructUri(spec);
    }

    private URI constructUri(JavaToolchainSpec spec) {
        return URI.create(getServerBaseUri() +
            "v3/binary/latest/" + getLanguageVersion(spec).toString() +
            "/" +
            determineReleaseState() +
            "/" +
            determineOs() +
            "/" +
            determineArch() +
            "/jdk/" +
            determineImplementation(spec) +
            "/normal/adoptopenjdk");
    }

    private String determineImplementation(JavaToolchainSpec spec) {
        final JvmImplementation implementation = spec.getImplementation().getOrNull();
        return implementation == JvmImplementation.J9 ? "openj9" : "hotspot";
    }

    public String toFilename(JavaToolchainSpec spec) {
        return String.format("adoptopenjdk-%s-%s-%s.%s", getLanguageVersion(spec).toString(), determineArch(), determineOs(), determineFileExtension());
    }

    private String determineFileExtension() {
        if (operatingSystem.isWindows()) {
            return "zip";
        }
        return "tar.gz";
    }

    private JavaLanguageVersion getLanguageVersion(JavaToolchainSpec spec) {
        return spec.getLanguageVersion().get();
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

    private String determineReleaseState() {
        return "ga";
    }

    private String getServerBaseUri() {
        String baseUri = rootUrl.getOrElse("https://api.adoptopenjdk.net/");
        if (!baseUri.endsWith("/")) {
            baseUri += "/";
        }
        return baseUri;
    }

}
