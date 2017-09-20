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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.List;

public class DefaultWindowsSdkLocator implements WindowsSdkLocator {
    private final WindowsSdkLocator legacyWindowsSdkLocator;
    private final WindowsKitComponentLocator<WindowsKitWindowsSdk> windowsKitWindowsSdkLocator;

    @VisibleForTesting
    DefaultWindowsSdkLocator(WindowsSdkLocator legacyWindowsSdkLocator, WindowsKitComponentLocator<WindowsKitWindowsSdk> windowsKitWindowsSdkLocator) {
        this.legacyWindowsSdkLocator = legacyWindowsSdkLocator;
        this.windowsKitWindowsSdkLocator = windowsKitWindowsSdkLocator;
    }

    public DefaultWindowsSdkLocator(OperatingSystem operatingSystem, WindowsRegistry windowsRegistry) {
        this(new LegacyWindowsSdkLocator(operatingSystem, windowsRegistry), new WindowsKitWindowsSdkLocator(windowsRegistry));
    }

    @Override
    public SearchResult locateWindowsSdks(File candidate) {
        return new SdkSearchResult(legacyWindowsSdkLocator.locateWindowsSdks(candidate), windowsKitWindowsSdkLocator.locateComponents(candidate));
    }

    @Override
    public List<WindowsSdk> locateAllWindowsSdks() {
        List<WindowsSdk> allSdks = Lists.newArrayList();
        allSdks.addAll(legacyWindowsSdkLocator.locateAllWindowsSdks());
        allSdks.addAll(windowsKitWindowsSdkLocator.locateAllComponents());
        return allSdks;
    }

    private static class SdkSearchResult implements SearchResult {
        final SearchResult legacySearchResult;
        final WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk> windowsKitSearchResult;

        SdkSearchResult(SearchResult legacySearchResult, WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk> windowsKitSearchResult) {
            this.legacySearchResult = legacySearchResult;
            this.windowsKitSearchResult = windowsKitSearchResult;
        }

        @Override
        public WindowsSdk getSdk() {
            if (windowsKitSearchResult.isAvailable()) {
                return windowsKitSearchResult.getComponent();
            } else if (legacySearchResult.isAvailable()) {
                return legacySearchResult.getSdk();
            } else {
                return null;
            }
        }

        @Override
        public boolean isAvailable() {
            return windowsKitSearchResult.isAvailable() || legacySearchResult.isAvailable();
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            legacySearchResult.explain(visitor);
        }
    }
}
