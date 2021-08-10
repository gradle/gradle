/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal;

import org.gradle.api.java.archives.ManifestMergeDetails;
import org.gradle.api.provider.Provider;
import org.gradle.internal.deprecation.DeprecationLogger;

public class DefaultManifestMergeDetails implements ManifestMergeDetails {
    private String section;
    private String key;
    private String baseValue;
    private String mergeValue;
    private Object value;
    private boolean excluded;

    public DefaultManifestMergeDetails(String section, String key, String baseValue, String mergeValue, Object value) {
        this.section = section;
        this.key = key;
        this.baseValue = baseValue;
        this.mergeValue = mergeValue;
        this.value = value;
    }

    @Override
    public String getSection() {
        return section;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getBaseValue() {
        return baseValue;
    }

    @Override
    public String getMergeValue() {
        return mergeValue;
    }

    @Override
    public String getValue() {
        if (value instanceof Provider) {
            DeprecationLogger.deprecateMethod(ManifestMergeDetails.class, "getValue()")
                .withAdvice("Please use #getRawValue() instead.")
                .willBeRemovedInGradle8()
                .withDslReference(ManifestMergeDetails.class, "rawValue")
                .nagUser();
        }
        return value.toString();
    }

    @Override
    public Object getRawValue() {
        return value;
    }

    public boolean isExcluded() {
        return excluded;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public void exclude() {
        excluded = true;
    }
}
