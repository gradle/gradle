/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.java.archives;

/**
 * Details of a value being merged from two different manifests.
 */
public interface ManifestMergeDetails {
    /**
     * Returns the section this merge details belongs to.
     */
    String getSection();

    /**
     * Returns the key that is to be merged.
     */
    String getKey();

    /**
     * Returns the value for the key of the manifest that is the target of the merge.
     */
    String getBaseValue();

    /**
     * Returns the value for the key of the manifest that is the source for the merge.
     */
    String getMergeValue();

    /**
     * Returns the value for the key of the manifest after the merge takes place. By default this is the value
     * of the source for the merge.
     */
    String getValue();

    /**
     * Set's the value for the key of the manifest after the merge takes place.
     *
     * @param value
     */
    void setValue(String value);

    /**
     * Excludes this key from being in the manifest after the merge.
     */
    void exclude();
}