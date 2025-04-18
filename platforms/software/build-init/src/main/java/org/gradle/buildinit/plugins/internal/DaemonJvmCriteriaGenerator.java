/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesGenerator;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.BuildPlatformFactory;
import org.gradle.platform.OperatingSystem;

import java.io.File;
import java.net.URI;
import java.util.Map;

import static org.gradle.buildinit.tasks.InitBuild.DEFAULT_JAVA_VERSION;

public class DaemonJvmCriteriaGenerator implements BuildContentGenerator {

    /**
     * Toolchain download URLs to enable auto-provisioning, obtained from latest foojay-plugin using
     * version {@link org.gradle.buildinit.tasks.InitBuild#DEFAULT_JAVA_VERSION} and vendor {@link org.gradle.jvm.toolchain.JvmVendorSpec#ADOPTIUM}
     *
     * The {@link DaemonJvmCriteriaGeneratorIntegrationTest} ensures those are kept valid and in sync to future changes
     */
    final static Map<BuildPlatform, URI> TOOLCHAIN_DEFAULT_DOWNLOAD_URLS = ImmutableMap.of(
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.FREE_BSD), URI.create("https://api.foojay.io/disco/v3.0/ids/e2d97f28068cf05b0467aa8e97b19f69/redirect"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.FREE_BSD), URI.create("https://api.foojay.io/disco/v3.0/ids/a41f952f4496c2309be30fd168c6c117/redirect"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.LINUX), URI.create("https://api.foojay.io/disco/v3.0/ids/e2d97f28068cf05b0467aa8e97b19f69/redirect"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.LINUX), URI.create("https://api.foojay.io/disco/v3.0/ids/a41f952f4496c2309be30fd168c6c117/redirect"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.MAC_OS), URI.create("https://api.foojay.io/disco/v3.0/ids/e7806cd9471741d622398825f14d2da6/redirect"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.MAC_OS), URI.create("https://api.foojay.io/disco/v3.0/ids/0402cc5012ae8124ea0ad01bd29342ef/redirect"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.UNIX), URI.create("https://api.foojay.io/disco/v3.0/ids/e2d97f28068cf05b0467aa8e97b19f69/redirect"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.UNIX), URI.create("https://api.foojay.io/disco/v3.0/ids/a41f952f4496c2309be30fd168c6c117/redirect"),
        BuildPlatformFactory.of(Architecture.AARCH64, OperatingSystem.WINDOWS), URI.create("https://api.foojay.io/disco/v3.0/ids/86ea5d26c5757681ffe78d87258b45ec/redirect"),
        BuildPlatformFactory.of(Architecture.X86_64, OperatingSystem.WINDOWS), URI.create("https://api.foojay.io/disco/v3.0/ids/ea8232621e1368089cec8b12816df5e3/redirect"));

    @Override
    public void generate(InitSettings settings, BuildContentGenerationContext buildContentGenerationContext) {
        File propertiesFile = settings.getTarget().file(DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE).getAsFile();
        DaemonJvmPropertiesGenerator.generate(propertiesFile, JavaLanguageVersion.of(DEFAULT_JAVA_VERSION), null, false, TOOLCHAIN_DEFAULT_DOWNLOAD_URLS);
    }
}
