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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Comparator;

@ServiceScope(Scope.Build.class)
public interface VersionComparator extends Comparator<Versioned> {
    /**
     * Compares two versioned elements to see which is the 'latest'.
     */
    @Override
    int compare(Versioned element1, Versioned element2);

    Comparator<Version> asVersionComparator();
}
