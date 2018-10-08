/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.msvcpp;

import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.CommandLineToolVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.DefaultVisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.DefaultVswhereVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.SystemPathVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionDeterminer;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VswhereVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.WindowsRegistryVersionLocator;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;

public class VisualStudioLocatorTestFixture {
    public static VisualStudioLocator getVisualStudioLocator() {
        VisualCppMetadataProvider visualCppMetadataProvider = new DefaultVisualCppMetadataProvider(NativeServicesTestFixture.getInstance().get(WindowsRegistry.class));
        VisualStudioVersionLocator commandLineLocator = new CommandLineToolVersionLocator(TestFiles.execActionFactory(), visualCppMetadataProvider, getVswhereLocator());
        VisualStudioVersionLocator windowsRegistryLocator = new WindowsRegistryVersionLocator(NativeServicesTestFixture.getInstance().get(WindowsRegistry.class));
        VisualStudioMetaDataProvider versionDeterminer = new VisualStudioVersionDeterminer(commandLineLocator, windowsRegistryLocator, visualCppMetadataProvider);
        VisualStudioVersionLocator systemPathLocator = new SystemPathVersionLocator(OperatingSystem.current(), versionDeterminer);
        return new DefaultVisualStudioLocator(commandLineLocator, windowsRegistryLocator, systemPathLocator, versionDeterminer, NativeServicesTestFixture.getInstance().get(SystemInfo.class));
    }

    public static VswhereVersionLocator getVswhereLocator() {
        return new DefaultVswhereVersionLocator(NativeServicesTestFixture.getInstance().get(WindowsRegistry.class), OperatingSystem.current());
    }
}
