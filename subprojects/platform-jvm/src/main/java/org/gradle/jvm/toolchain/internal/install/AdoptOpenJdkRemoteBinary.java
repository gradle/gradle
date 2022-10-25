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

package org.gradle.jvm.toolchain.internal.install;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.platform.BuildPlatform;

import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;

public class AdoptOpenJdkRemoteBinary {

    private static final String DEFAULT_ADOPTOPENJDK_ROOT_URL = "https://api.adoptopenjdk.net/";
    private static final String DEFAULT_ADOPTIUM_ROOT_URL = "https://api.adoptium.net/";

    private final Provider<String> adoptOpenJdkRootUrl;
    private final Provider<String> adoptiumRootUrl;

    @Inject
    public AdoptOpenJdkRemoteBinary(ProviderFactory providerFactory) {
        this.adoptOpenJdkRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri");
        this.adoptiumRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptium.baseUri");
    }

    public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) {
        JavaToolchainSpec spec = request.getJavaToolchainSpec();
        if (canProvide(spec)) {
            return Optional.of(JavaToolchainDownload.fromUri(constructUri(spec, request.getBuildPlatform())));
        } else {
            return Optional.empty();
        }
    }

    private boolean canProvide(JavaToolchainSpec spec) {
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

    private URI constructUri(JavaToolchainSpec spec, BuildPlatform platform) {
        return URI.create(determineServerBaseUri(spec) +
                "v3/binary/latest/" + determineLanguageVersion(spec) +
                "/" +
                determineReleaseState() +
                "/" +
                determineOs(platform) +
                "/" +
                determineArch(platform) +
                "/jdk/" +
                determineImplementation(spec) +
                "/normal/" +
                determineOrganization(spec));
    }

    private static String determineImplementation(JavaToolchainSpec spec) {
        return isJ9Requested(spec) ? "openj9" : "hotspot";
    }

    private static JavaLanguageVersion determineLanguageVersion(JavaToolchainSpec spec) {
        return spec.getLanguageVersion().get();
    }

    private String determineArch(BuildPlatform platform) {
        switch (platform.getArchitecture()) {
            case X86:
                return "x32";
            case X86_64:
                return "x64";
            case AARCH64:
                return "aarch64";
            default:
                return "unknown";
        }
    }

    private String determineOs(BuildPlatform platform) {
        switch (platform.getOperatingSystem()) {
            case LINUX:
                return "linux";
            case WINDOWS:
                return "windows";
            case MAC_OS:
                return "mac";
            case SOLARIS:
                return "solaris";
            default:
                return "unknown";
        }
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
