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
package org.gradle.api.internal;

import com.google.common.collect.Sets;

import java.util.Set;

public class ExperimentalFeatures {
    private static final String ENABLE_EXPERIMENTAL_FEATURES = "org.gradle.internal.experimentalFeatures";
    private final Set<String> enabledFeatures = Sets.newHashSet();
    boolean allEnabled;

    public boolean isEnabled() {
        return allEnabled
            || System.getProperty(ENABLE_EXPERIMENTAL_FEATURES) != null;
    }

    public boolean isEnabled(String featureName) {
        return isEnabled()
            || enabledFeatures.contains(featureName)
            || System.getProperty(ENABLE_EXPERIMENTAL_FEATURES + "." + featureName) != null;
    }

    public void enableAll() {
        allEnabled = true;
    }

    public void enable(String featureName) {
        enabledFeatures.add(featureName);
    }
}
