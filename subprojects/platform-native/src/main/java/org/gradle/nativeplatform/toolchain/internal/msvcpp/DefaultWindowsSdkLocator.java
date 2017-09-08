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

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

public class DefaultWindowsSdkLocator implements WindowsSdkLocator {
    private final LegacyWindowsSdkLocator legacyWindowsSdkLocator;
    private final WindowsKitWindowsSdkLocator windowsKitWindowsSdkLocator;

    public DefaultWindowsSdkLocator(LegacyWindowsSdkLocator legacyWindowsSdkLocator, WindowsKitWindowsSdkLocator windowsKitWindowsSdkLocator) {
        this.legacyWindowsSdkLocator = legacyWindowsSdkLocator;
        this.windowsKitWindowsSdkLocator = windowsKitWindowsSdkLocator;
    }

    @Override
    public SearchResult locateWindowsSdks(File candidate) {
        return new SdkSearchResult(legacyWindowsSdkLocator.locateWindowsSdks(candidate), windowsKitWindowsSdkLocator.locateComponents(candidate));
    }

    @Override
    public List<SearchResult> locateAllWindowsSdks() {
        List<SearchResult> allSdks = Lists.newArrayList();
        allSdks.addAll(legacyWindowsSdkLocator.locateAllWindowsSdks());
        allSdks.addAll(collect(windowsKitWindowsSdkLocator.locateAllComponents(), new Transformer<SearchResult, WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk>>() {
            @Override
            public SearchResult transform(WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk> searchResult) {
                return new SearchResultAdapter(searchResult);
            }
        }));
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

    private static class SearchResultAdapter implements SearchResult {
        final WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk> delegate;

        public SearchResultAdapter(WindowsKitComponentLocator.SearchResult<WindowsKitWindowsSdk> delegate) {
            this.delegate = delegate;
        }

        @Override
        public WindowsSdk getSdk() {
            return delegate.getComponent();
        }

        @Override
        public boolean isAvailable() {
            return delegate.isAvailable();
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            delegate.explain(visitor);
        }
    }
}
