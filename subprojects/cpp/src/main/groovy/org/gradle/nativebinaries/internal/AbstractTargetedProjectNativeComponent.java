/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativebinaries.internal;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.platform.Platform;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractTargetedProjectNativeComponent extends AbstractProjectNativeComponent implements TargetedNativeComponentInternal {

    private final Set<String> targetPlatforms = new HashSet<String>();
    private final Set<String> buildTypes = new HashSet<String>();
    private final Set<String> flavors = new HashSet<String>();

    public AbstractTargetedProjectNativeComponent(NativeProjectComponentIdentifier id) {
        super(id);
    }

    public void targetFlavors(String... flavorSelectors) {
        Collections.addAll(flavors, flavorSelectors);
    }

    public void targetPlatforms(String... platformSelectors) {
        Collections.addAll(targetPlatforms, platformSelectors);
    }

    public void targetBuildTypes(String... buildTypeSelectors) {
        Collections.addAll(buildTypes, buildTypeSelectors);
    }

    public Set<Flavor> chooseFlavors(Set<? extends Flavor> candidates) {
        return chooseElements(Flavor.class, candidates, flavors);
    }

    public Set<BuildType> chooseBuildTypes(Set<? extends BuildType> candidates) {
        return chooseElements(BuildType.class, candidates, buildTypes);
    }

    public Set<Platform> choosePlatforms(Set<? extends Platform> candidates) {
        return chooseElements(Platform.class, candidates, targetPlatforms);
    }

    private <T extends Named> Set<T> chooseElements(Class<T> type, Set<? extends T> candidates, final Set<String> names) {
        if (names.isEmpty()) {
            return new LinkedHashSet<T>(candidates);
        }

        Set<String> unusedNames = new HashSet<String>(names);
        Set<T> chosen = new LinkedHashSet<T>();
        for (T candidate : candidates) {
            if (unusedNames.remove(candidate.getName())) {
                chosen.add(candidate);
            }
        }

        if (!unusedNames.isEmpty()) {
            throw new InvalidUserDataException(String.format("Invalid %s: '%s'", type.getSimpleName(), unusedNames.iterator().next()));
        }

        return chosen;
    }
}