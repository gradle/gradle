/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.file;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.io.File;
import java.util.List;

public class DefaultReservedFileLocationRegistry implements ReservedFileLocationRegistry {
    private final FileHierarchySet reservedLocations;

    public DefaultReservedFileLocationRegistry(List<ReservedFileLocation> registeredReservedLocations) {
        this.reservedLocations = DefaultFileHierarchySet.of(Iterables.transform(registeredReservedLocations, new Function<ReservedFileLocation, File>() {
            @Override
            public File apply(ReservedFileLocation input) {
                return input.getReservedLocation().get().getAsFile();
            }
        }));
    }

    @Override
    public boolean isInReservedFileLocation(File location) {
        return reservedLocations.contains(location);
    }
}
