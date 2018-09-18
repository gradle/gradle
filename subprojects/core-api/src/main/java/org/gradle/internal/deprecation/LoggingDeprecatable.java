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

package org.gradle.internal.deprecation;

import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.util.DeprecationLogger;

import java.util.HashSet;
import java.util.Set;

public class LoggingDeprecatable implements Deprecatable {

    private final Set<String> deprecations = new HashSet<String>();

    @Override
    public void addDeprecation(String deprecation) {
        deprecations.add(deprecation);
    }

    @Override
    public Set<String> getDeprecations() {
        return deprecations;
    }

    @Override
    public void checkDeprecation() {
        String suffix = LoggingDeprecatedFeatureHandler.getRemovalDetails();
        for (String deprecation : deprecations) {
            DeprecationLogger.nagUserWithDeprecatedBuildInvocationFeature(deprecation, String.format("This %s", suffix), null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LoggingDeprecatable that = (LoggingDeprecatable) o;

        return deprecations != null ? deprecations.equals(that.deprecations) : that.deprecations == null;
    }

    @Override
    public int hashCode() {
        return deprecations != null ? deprecations.hashCode() : 0;
    }
}
