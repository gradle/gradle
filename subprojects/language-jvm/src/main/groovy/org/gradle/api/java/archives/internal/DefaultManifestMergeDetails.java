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

public class DefaultManifestMergeDetails implements ManifestMergeDetails {
    private String section;
    private String key;
    private String baseValue;
    private String mergeValue;
    private String value;
    private boolean excluded;

    public DefaultManifestMergeDetails(String section, String key, String baseValue, String mergeValue, String value) {
        this.section = section;
        this.key = key;
        this.baseValue = baseValue;
        this.mergeValue = mergeValue;
        this.value = value;
    }

    public String getSection() {
        return section;
    }

    public String getKey() {
        return key;
    }

    public String getBaseValue() {
        return baseValue;
    }

    public String getMergeValue() {
        return mergeValue;
    }

    public String getValue() {
        return value;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void exclude() {
        excluded = true;
    }
}