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

package org.gradle.api.plugins.buildcomparison.compare.internal;

/**
 * An object that can compare builds, according to a specification.
 */
public interface BuildComparator {

    /**
     * Performs the comparison, according to the given specification.
     *
     * @param spec The specification of the comparison.
     * @return The result of the comparison. Never null.
     */
    BuildComparisonResult compareBuilds(BuildComparisonSpec spec);

}
