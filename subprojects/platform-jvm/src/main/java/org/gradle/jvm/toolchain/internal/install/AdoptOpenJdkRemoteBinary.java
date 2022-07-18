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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.jvm.toolchain.JavaToolchainSpecVersion;

import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;

public abstract class AdoptOpenJdkRemoteBinary implements JavaToolchainRepository {

    private static final String DEFAULT_ADOPTOPENJDK_ROOT_URL = "https://api.adoptopenjdk.net/";
    private static final String DEFAULT_ADOPTIUM_ROOT_URL = "https://api.adoptium.net/";

    private final Provider<String> adoptOpenJdkRootUrl;
    private final Provider<String> adoptiumRootUrl;

    @Inject
    public AdoptOpenJdkRemoteBinary(ProviderFactory providerFactory) {
        this.adoptOpenJdkRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri");
        this.adoptiumRootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptium.baseUri");
    }

    protected OperatingSystem operatingSystem() {
        return OperatingSystem.current(); //TODO (#21082): hack, can't inject it since turning this class into a build service; should be part of the toolchain spec anyways !
    }

    protected SystemInfo systemInfo() {
        return Native.get(SystemInfo.class); //TODO (#21082): hack, can't inject it since turning this class into a build service; should be part of the toolchain spec anyways !
    }

    @Override
    public Optional<URI> toUri(JavaToolchainSpec spec) {
        if (canProvide(spec)) {
            return Optional.of(constructUri(spec));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Metadata> toMetadata(JavaToolchainSpec spec) {
        if (canProvide(spec)) {
            return Optional.of(new MetadataImpl(spec));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public JavaToolchainSpecVersion getToolchainSpecCompatibility() {
        return JavaToolchainSpecVersion.V1;
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

    private String determineFileExtension() {
        if (operatingSystem().isWindows()) {
            return "zip";
        }
        return "tar.gz";
    }

    private static JavaLanguageVersion determineLanguageVersion(JavaToolchainSpec spec) {
        return spec.getLanguageVersion().get();
    }

    private String determineArch() {
        switch (systemInfo().getArchitecture()) {
            case i386:
                return "x32";
            case amd64:
                return "x64";
            case aarch64:
                return "aarch64";
        }
        return systemInfo().getArchitectureName();
    }

    private String determineOs() {
        if (operatingSystem().isWindows()) {
            return "windows";
        } else if (operatingSystem().isMacOsX()) {
            return "mac";
        } else if (operatingSystem().isLinux()) {
            return "linux";
        }
        return operatingSystem().getFamilyName();
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

    private class MetadataImpl implements Metadata {

        private final JavaToolchainSpec spec;

        public MetadataImpl(JavaToolchainSpec spec) {
            this.spec = spec;
        }

        @Override
        public String fileExtension() {
            return determineFileExtension();
        }

        @Override
        public String vendor() {
            return determineVendor(spec);
        }

        @Override
        public String languageLevel() {
            return determineLanguageVersion(spec).toString();
        }

        @Override
        public String operatingSystem() {
            return determineOs();
        }

        @Override
        public String implementation() {
            return determineImplementation(spec);
        }

        @Override
        public String architecture() {
            return determineArch();
        }

        @Override
        public String toString() {
            return "fileExtension: " + fileExtension() + ", vendor: " + vendor() + ", languageLevel: " + languageLevel() +
                    "operatingSystem: " + operatingSystem() + ", implementation: " + implementation() + ", architecture: " + architecture();
        }
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
