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
package org.gradle.nativeplatform.internal;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;

import java.util.*;

public abstract class AbstractTargetedNativeComponentSpec extends AbstractNativeComponentSpec implements TargetedNativeComponentInternal {

    private final List<PlatformRequirement> targetPlatforms = Lists.newArrayList();
    private final Set<String> buildTypes = new HashSet<String>();
    private final Set<String> flavors = new HashSet<String>();

    @Override
    public List<PlatformRequirement> getTargetPlatforms() {
        return Collections.unmodifiableList(targetPlatforms);
    }

    @Override
    public void targetPlatform(String targetPlatform) {
        this.targetPlatforms.add(DefaultPlatformRequirement.create(targetPlatform));
    }

    @Override
    public void targetFlavors(String... flavorSelectors) {
        Collections.addAll(flavors, flavorSelectors);
    }

    @Override
    public void targetBuildTypes(String... buildTypeSelectors) {
        Collections.addAll(buildTypes, buildTypeSelectors);
    }

    @Override
    public Set<Flavor> chooseFlavors(Set<? extends Flavor> candidates) {
        return chooseElements(Flavor.class, candidates, flavors);
    }

    @Override
    public Set<BuildType> chooseBuildTypes(Set<? extends BuildType> candidates) {
        return chooseElements(BuildType.class, candidates, buildTypes);
    }

    protected <T extends Named> Set<T> chooseElements(Class<T> type, Set<? extends T> candidates, Set<String> names) {
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
