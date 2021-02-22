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
package org.gradle.api.internal.std;

public class ImportConfiguration {
    private final IncludeExcludePredicate<String> libraries;
    private final IncludeExcludePredicate<String> bundles;
    private final IncludeExcludePredicate<String> versions;
    private final IncludeExcludePredicate<String> plugins;

    public ImportConfiguration(IncludeExcludePredicate<String> libraries,
                               IncludeExcludePredicate<String> bundles,
                               IncludeExcludePredicate<String> versions,
                               IncludeExcludePredicate<String> plugins) {
        this.libraries = libraries;
        this.bundles = bundles;
        this.versions = versions;
        this.plugins = plugins;
    }

    public boolean includeLibrary(String name) {
        return libraries.test(name);
    }

    public boolean includeBundle(String bundleName) {
        return bundles.test(bundleName);
    }

    public boolean includeVersion(String version) {
        return versions.test(version);
    }

    public boolean includePlugin(String id) {
        return plugins.test(id);
    }
}
