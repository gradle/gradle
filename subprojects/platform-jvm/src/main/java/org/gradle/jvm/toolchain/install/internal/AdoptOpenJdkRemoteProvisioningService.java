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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainCandidate;
import org.gradle.jvm.toolchain.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.JavaToolchainProvisioningDetails;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;

public class AdoptOpenJdkRemoteProvisioningService implements JavaToolchainProvisioningService {

    private final JdkDownloader downloader;
    private final Provider<String> rootUrl;

    @Inject
    public AdoptOpenJdkRemoteProvisioningService(JdkDownloader downloader, ProviderFactory providerFactory) {
        this.downloader = downloader;
        rootUrl = providerFactory.gradleProperty("org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri").forUseAtConfigurationTime();
    }

    @Override
    public LazyProvisioner provisionerFor(JavaToolchainCandidate candidate) {
        return new LazyProvisioner() {
            @Override
            public String getFileName() {
                return fileNameOf(candidate);
            }

            @Override
            public boolean provision(File destination) {
                URI source = constructUri(candidate);
                downloader.download(source, destination);
                return true;
            }
        };
    }

    public String fileNameOf(JavaToolchainCandidate candidate) {
        String osString = candidate.getOperatingSystem();
        String archString = candidate.getArch();
        String ext = "windows".equals(osString) ? "zip" : "tar.gz";
        return String.format("%s-%s-%s-%s.%s", candidate.getVendor(), candidate.getLanguageVersion(), archString, osString, ext);
    }

    @Override
    public void findCandidates(JavaToolchainProvisioningDetails details) {
        JavaToolchainSpec spec = details.getRequested();
        JavaLanguageVersion version = spec.getLanguageVersion().get();
        boolean matchesVendor = !spec.getVendor().isPresent() || spec.getVendor().get().matches("adoptopenjdk");
        if (version.canCompileOrRun(8) && matchesVendor) {
            JavaToolchainCandidate.BuilderWithLanguageVersion builder = details.newCandidate()
                .withVendor("adoptopenjdk")
                .withLanguageVersion(version.asInt());
            if (spec.getImplementation().isPresent()) {
                builder = builder.withImplementation(spec.getImplementation().get());
            }
            details.listCandidates(ImmutableList.of(builder.build()));
        }
    }

    @VisibleForTesting
    public URI constructUri(JavaToolchainCandidate candidate) {
        String uriString = getServerBaseUri() +
            "v3/binary/latest/" + candidate.getLanguageVersion() +
            "/" +
            determineReleaseState() +
            "/" +
            candidate.getOperatingSystem() +
            "/" +
            candidate.getArch() +
            "/jdk/" +
            determineImplementation(candidate.getImplementation().orElse(null)) +
            "/normal/adoptopenjdk";
        return URI.create(uriString);
    }

    private String determineImplementation(@Nullable JvmImplementation implementation) {
        return implementation == JvmImplementation.J9 ? "openj9" : "hotspot";
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
