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

package org.gradle.internal.resource.local;

import org.gradle.internal.hash.HashCode;

import java.util.LinkedList;
import java.util.List;

public class CompositeLocallyAvailableResourceFinder<C> implements LocallyAvailableResourceFinder<C> {

    private final List<LocallyAvailableResourceFinder<C>> composites;

    public CompositeLocallyAvailableResourceFinder(List<LocallyAvailableResourceFinder<C>> composites) {
        this.composites = composites;
    }

    @Override
    public LocallyAvailableResourceCandidates findCandidates(C criterion) {
        List<LocallyAvailableResourceCandidates> allCandidates = new LinkedList<LocallyAvailableResourceCandidates>();
        for (LocallyAvailableResourceFinder<C> finder : composites) {
            allCandidates.add(finder.findCandidates(criterion));
        }

        return new CompositeLocallyAvailableResourceCandidates(allCandidates);
    }

    private static class CompositeLocallyAvailableResourceCandidates implements LocallyAvailableResourceCandidates {
        private final List<LocallyAvailableResourceCandidates> allCandidates;

        public CompositeLocallyAvailableResourceCandidates(List<LocallyAvailableResourceCandidates> allCandidates) {
            this.allCandidates = allCandidates;
        }

        @Override
        public boolean isNone() {
            for (LocallyAvailableResourceCandidates candidates : allCandidates) {
                if (!candidates.isNone()) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public LocallyAvailableResource findByHashValue(HashCode hashValue) {
            for (LocallyAvailableResourceCandidates candidates : allCandidates) {
                LocallyAvailableResource match = candidates.findByHashValue(hashValue);
                if (match != null) {
                    return match;
                }
            }

            return null;
        }
    }
}
