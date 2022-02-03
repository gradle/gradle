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
import org.gradle.internal.deprecation.DeprecationLogger;
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
        rootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri");
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
        final boolean matchesLanguageVersion = determineLanguageVersion(spec).canCompileOrRun(8);
        boolean j9Requested = spec.getImplementation().get() == JvmImplementation.J9;
        boolean matchesVendor = matchesVendor(spec, j9Requested);
        return matchesLanguageVersion && matchesVendor;
    }

    private boolean matchesVendor(JavaToolchainSpec spec, boolean j9Requested) {
        final DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        if (vendorSpec == DefaultJvmVendorSpec.any()) {
            return true;
        }

        if (vendorSpec.test(JvmVendor.KnownJvmVendor.ADOPTOPENJDK.asJvmVendor())) {
            DeprecationLogger.deprecateBehaviour("Due to changes in AdoptOpenJDK download endpoint, downloading a JDK with an explicit vendor of AdoptOpenJDK should be replaced with a spec without a vendor or using Eclipse Temurin / IBM Semeru.")
                .willBeRemovedInGradle8().withUpgradeGuideSection(7, "adoptopenjdk_download").nagUser();
            return true;
        }

        if (vendorSpec.test(JvmVendor.KnownJvmVendor.ADOPTIUM.asJvmVendor()) && !j9Requested) {
            return true;
        }

        return vendorSpec.test(JvmVendor.KnownJvmVendor.IBM_SEMERU.asJvmVendor());
    }

    URI toDownloadUri(JavaToolchainSpec spec) {
        return constructUri(spec);
    }

    private URI constructUri(JavaToolchainSpec spec) {
        return URI.create(getServerBaseUri() +
            "v3/binary/latest/" + determineLanguageVersion(spec).toString() +
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
        String openj9 = "openj9";
        if (implementation == JvmImplementation.J9) {
            return openj9;
        }
        DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        if (vendorSpec != DefaultJvmVendorSpec.any() && vendorSpec.test(JvmVendor.KnownJvmVendor.IBM_SEMERU.asJvmVendor())) {
            return openj9;
        }
        return "hotspot";
    }

    private String determineVendor(JavaToolchainSpec spec) {
        DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        if (vendorSpec == DefaultJvmVendorSpec.any()) {
            return "adoptium";
        } else {
            return vendorSpec.toString().toLowerCase();
        }
    }

    public String toFilename(JavaToolchainSpec spec) {
        return String.format("%s-%s-%s-%s-%s.%s", determineVendor(spec), determineLanguageVersion(spec), determineArch(), determineImplementation(spec), determineOs(), determineFileExtension());
    }

    private String determineFileExtension() {
        if (operatingSystem.isWindows()) {
            return "zip";
        }
        return "tar.gz";
    }

    private JavaLanguageVersion determineLanguageVersion(JavaToolchainSpec spec) {
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
