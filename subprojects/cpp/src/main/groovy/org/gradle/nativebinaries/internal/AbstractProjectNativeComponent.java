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

import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.nativebinaries.BuildType;
import org.gradle.nativebinaries.Flavor;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.util.GUtil;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractProjectNativeComponent implements ProjectNativeComponentInternal {
    private final NotationParser<Object, Set<LanguageSourceSet>> sourcesNotationParser = SourceSetNotationParser.parser();
    private final NativeProjectComponentIdentifier id;
    private final DomainObjectSet<LanguageSourceSet> sourceSets;
    private final DefaultDomainObjectSet<NativeBinary> binaries;
    private final Set<String> targetPlatforms = new HashSet<String>();
    private final Set<String> buildTypes = new HashSet<String>();
    private final Set<String> flavors = new HashSet<String>();
    private String baseName;

    public AbstractProjectNativeComponent(NativeProjectComponentIdentifier id) {
        this.id = id;
        this.sourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
        binaries = new DefaultDomainObjectSet<NativeBinary>(NativeBinary.class);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getProjectPath() {
        return id.getProjectPath();
    }

    public String getName() {
        return id.getName();
    }

    public DomainObjectSet<LanguageSourceSet> getSource() {
        return sourceSets;
    }

    public void source(Object sources) {
        sourceSets.addAll(sourcesNotationParser.parseNotation(sources));
    }

    public DomainObjectSet<NativeBinary> getBinaries() {
        return binaries;
    }

    public String getBaseName() {
        return GUtil.elvis(baseName, getName());
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public void targetFlavors(Object... flavorSelectors) {
        for (Object flavorSelector : flavorSelectors) {
            // TODO:DAZ Allow Flavor instance and selector
            assert flavorSelector instanceof String;
            flavors.add((String) flavorSelector);
        }
    }

    public void targetPlatforms(Object... platformSelectors) {
        for (Object platformSelector : platformSelectors) {
            // TODO:DAZ Allow Platform instance and selector
            assert platformSelector instanceof String;
            targetPlatforms.add((String) platformSelector);
        }
    }

    public void targetBuildTypes(Object... buildTypeSelectors) {
        for (Object buildTypeSelector : buildTypeSelectors) {
            // TODO:DAZ Allow BuildType instance and selector
            assert buildTypeSelector instanceof String;
            buildTypes.add((String) buildTypeSelector);
        }
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