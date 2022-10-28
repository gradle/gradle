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

package org.gradle.api.internal.tasks.testing;

import java.util.regex.Pattern;

/**
 * Default implementation of {@link DistributionModule}.
 */
public class DefaultDistributionModule implements DistributionModule {
    private final String moduleName;
    private final Pattern fileNameMatcher;

    public DefaultDistributionModule(String moduleName, Pattern fileNameMatcher) {
        this.moduleName = moduleName;
        this.fileNameMatcher = fileNameMatcher;
    }

    @Override
    public String getModuleName() {
        return moduleName;
    }

    @Override
    public Pattern getFileNameMatcher() {
        return fileNameMatcher;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultDistributionModule that = (DefaultDistributionModule) o;

        if (!moduleName.equals(that.moduleName)) {
            return false;
        }
        return fileNameMatcher.equals(that.fileNameMatcher);
    }

    @Override
    public int hashCode() {
        int result = moduleName.hashCode();
        result = 31 * result + fileNameMatcher.hashCode();
        return result;
    }
}
