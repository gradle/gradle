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

    private static final String DEFAULT_ADOPTOPENJDK_ROOT_URL = "https://api.adoptopenjdk.net/";
    private static final String DEFAULT_ADOPTIUM_ROOT_URL = "https://api.adoptium.net/";

    private final SystemInfo systemInfo;
    private final OperatingSystem operatingSystem;
    private final AdoptOpenJdkDownloader downloader;
    private final Provider<String> adoptOpenJdkRootUrl;
    private final Provider<String> adoptiumRootUrl;

    @Inject
    public AdoptOpenJdkRemoteBinary(SystemInfo systemInfo, OperatingSystem operatingSystem, AdoptOpenJdkDownloader downloader, ProviderFactory providerFactory) {
        this.systemInfo = systemInfo;
        this.operatingSystem = operatingSystem;
        this.downloader = downloader;
        this.adoptOpenJdkRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri");
        this.adoptiumRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptium.baseUri");
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
        boolean matchesVendor = matchesVendor(spec);
        return matchesLanguageVersion && matchesVendor;
    }

    private boolean matchesVendor(JavaToolchainSpec spec) {
        final DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        if (vendorSpec == DefaultJvmVendorSpec.any()) {
            return true;
        }

        if (vendorSpec.test(JvmVendor.KnownJvmVendor.ADOPTOPENJDK.asJvmVendor())) {
            DeprecationLogger.deprecateBehaviour("Due to changes in AdoptOpenJDK download endpoint, downloading a JDK with an explicit vendor of AdoptOpenJDK should be replaced with a spec without a vendor or using Eclipse Temurin / IBM Semeru.")
                    .willBeRemovedInGradle8().withUpgradeGuideSection(7, "adoptopenjdk_download").nagUser();
            return true;
        }

        if (vendorSpec.test(JvmVendor.KnownJvmVendor.ADOPTIUM.asJvmVendor()) && !isJ9ExplicitlyRequested(spec)) {
            return true;
        }

        return vendorSpec.test(JvmVendor.KnownJvmVendor.IBM_SEMERU.asJvmVendor());
    }

    URI toDownloadUri(JavaToolchainSpec spec) {
        return constructUri(spec);
    }

    private URI constructUri(JavaToolchainSpec spec) {
        return URI.create(determineServerBaseUri(spec) +
                "v3/binary/latest/" + determineLanguageVersion(spec) +
                "/" +
                determineReleaseState() +
                "/" +
                determineOs() +
                "/" +
                determineArch() +
                "/jdk/" +
                determineImplementation(spec) +
                "/normal/" +
                determineOrganization(spec));
    }

    private static String determineImplementation(JavaToolchainSpec spec) {
        return isJ9Requested(spec) ? "openj9" : "hotspot";
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

    private static JavaLanguageVersion determineLanguageVersion(JavaToolchainSpec spec) {
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

    private static String determineReleaseState() {
        return "ga";
    }

    private String determineServerBaseUri(JavaToolchainSpec spec) {
        String baseUri = adoptiumHasIt(spec) ? adoptiumRootUrl.getOrElse(DEFAULT_ADOPTIUM_ROOT_URL) :
                adoptOpenJdkRootUrl.getOrElse(DEFAULT_ADOPTOPENJDK_ROOT_URL);

        if (!baseUri.endsWith("/")) {
            baseUri += "/";
        }
        return baseUri;
    }

    private static String determineOrganization(JavaToolchainSpec spec) {
        return adoptiumHasIt(spec) ? "eclipse" : "adoptopenjdk";
    }

    private static boolean adoptiumHasIt(JavaToolchainSpec spec) { //TODO: also consider if ADOPTIUM was explicitly requested as the VENDOR?
        if (isJ9Requested(spec)) {
            return false;
        }

        int version = determineLanguageVersion(spec).asInt();
        return version == 8 || version == 11 || version >= 16; // https://api.adoptium.net/v3/info/available_releases
    }

    private static boolean isJ9Requested(JavaToolchainSpec spec) {
        if (isJ9ExplicitlyRequested(spec)) {
            return true;
        }
        return isJ9RequestedViaVendor(spec);
    }

    private static boolean isJ9ExplicitlyRequested(JavaToolchainSpec spec) {
        return spec.getImplementation().get() == JvmImplementation.J9;
    }

    private static boolean isJ9RequestedViaVendor(JavaToolchainSpec spec) {
        DefaultJvmVendorSpec vendorSpec = (DefaultJvmVendorSpec) spec.getVendor().get();
        return vendorSpec != DefaultJvmVendorSpec.any() && vendorSpec.test(JvmVendor.KnownJvmVendor.IBM_SEMERU.asJvmVendor());
    }

}
