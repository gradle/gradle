/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public interface LatestStrategy extends Comparator<Versioned> {
    /**
     * Finds the latest among the given versioned elements. The definition of 'latest'
     * depends on the strategy itself.
     */
    <T extends Versioned> T findLatest(Collection<T> elements);

    /**
     * Sorts the given versioned elements from oldest to latest. The definition of
     * 'latest' depends on the strategy itself.
     */
    <T extends Versioned> List<T> sort(Collection<T> elements);

    /**
     * Compares two versioned elements to see which is the 'latest'. The definition of
     * 'latest' depends on the strategy itself.
     */
    int compare(Versioned element1, Versioned element2);
}
