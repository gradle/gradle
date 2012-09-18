/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal;

import java.io.File;

/**
 * Locates documentation for various features.
 */
public class DocumentationRegistry {
    private final GradleDistributionLocator locator;

    public DocumentationRegistry(GradleDistributionLocator locator) {
        this.locator = locator;
    }

    /**
     * Returns the location the documentation for the given feature, referenced by id. The location may be local or remote.
     */
    public String getDocumentationFor(String id) {
        if (locator.getGradleHome() != null) {
            File pageLocation = new File(locator.getGradleHome(), String.format("docs/userguide/%s.html", id));
            File userGuideLocation = new File(locator.getGradleHome(), "docs/userguide/userguide.html");
            if (pageLocation.isFile() && userGuideLocation.isFile()) {
                return pageLocation.getAbsolutePath();
            }
            if (!pageLocation.isFile() && userGuideLocation.isFile()) {
                throw new IllegalArgumentException(String.format("User guide page '%s' not found.", pageLocation));
            }
            if (pageLocation.isFile() && !userGuideLocation.isFile()) {
                throw new IllegalArgumentException(String.format("User guide page '%s' not found.", userGuideLocation));
            }
        }
        return String.format("http://gradle.org/docs/current/userguide/%s.html", id);
    }

    public String getFeatureLifecycle() {
        return getDocumentationFor("feature_lifecycle");
    }
}
