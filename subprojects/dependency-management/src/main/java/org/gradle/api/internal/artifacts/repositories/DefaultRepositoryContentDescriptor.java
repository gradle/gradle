/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

class DefaultRepositoryContentDescriptor implements RepositoryContentDescriptorInternal {
    private Set<String> includedConfigurations;
    private Set<String> excludedConfigurations;
    private Set<ContentSpec> includeSpecs;
    private Set<ContentSpec> excludeSpecs;
    private Map<Attribute<Object>, Set<Object>> requiredAttributes;
    private boolean locked;

    private Action<? super ArtifactResolutionDetails> cachedAction;
    private final Supplier<String> repositoryNameSupplier;
    private final VersionSelectorScheme versionSelectorScheme;
    private final ConcurrentHashMap<String, VersionSelector> versionSelectors = new ConcurrentHashMap<>();

    public DefaultRepositoryContentDescriptor(Supplier<String> repositoryNameSupplier) {
        this.versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser());
        this.repositoryNameSupplier = repositoryNameSupplier;
    }

    private void assertMutable() {
        if (locked) {
            throw new IllegalStateException("Cannot mutate content repository descriptor '" +
                repositoryNameSupplier.get() +
                "' after repository has been used");
        }
    }

    @Override
    public Action<? super ArtifactResolutionDetails> toContentFilter() {
        if (cachedAction != null) {
            return cachedAction;
        }
        locked = true;
        if (includedConfigurations == null &&
                excludedConfigurations == null &&
                includeSpecs == null &&
                excludeSpecs == null &&
                requiredAttributes == null) {
            // no filtering in place
            return Actions.doNothing();
        }
        cachedAction = new RepositoryFilterAction(createSpecMatchers(includeSpecs), createSpecMatchers(excludeSpecs));
        return cachedAction;
    }

    @Override
    public RepositoryContentDescriptorInternal asMutableCopy() {
        DefaultRepositoryContentDescriptor copy = new DefaultRepositoryContentDescriptor(repositoryNameSupplier);
        if (includedConfigurations != null) {
            copy.includedConfigurations = Sets.newHashSet(includedConfigurations);
        }
        if (excludedConfigurations != null) {
            copy.excludedConfigurations = Sets.newHashSet(excludedConfigurations);
        }
        if (includeSpecs != null) {
            copy.includeSpecs = Sets.newHashSet(includeSpecs);
        }
        if (excludeSpecs != null) {
            copy.excludeSpecs = Sets.newHashSet(excludeSpecs);
        }
        if (requiredAttributes != null) {
            copy.requiredAttributes = Maps.newHashMap(requiredAttributes);
        }
        return copy;
    }

    @Nullable
    private static ImmutableList<SpecMatcher> createSpecMatchers(@Nullable Set<ContentSpec> specs) {
        ImmutableList<SpecMatcher> matchers = null;
        if (specs != null) {
            ImmutableList.Builder<SpecMatcher> builder = ImmutableList.builderWithExpectedSize(specs.size());
            for (ContentSpec spec : specs) {
                builder.add(spec.toMatcher());
            }
            matchers = builder.build();
        }
        return matchers;
    }

    @Override
    public void includeGroup(String group) {
        checkNotNull(group, "Group cannot be null");
        addInclude(group, null, null, false);
    }

    private static void checkNotNull(@Nullable String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    public void includeGroupByRegex(String groupRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        addInclude(groupRegex, null, null, true);
    }

    @Override
    public void includeModule(String group, String moduleName) {
        checkNotNull(group, "Group cannot be null");
        checkNotNull(moduleName, "Module name cannot be null");
        addInclude(group, moduleName, null, false);
    }

    @Override
    public void includeModuleByRegex(String groupRegex, String moduleNameRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        checkNotNull(moduleNameRegex, "Module name cannot be null");
        addInclude(groupRegex, moduleNameRegex, null, true);
    }

    @Override
    public void includeVersion(String group, String moduleName, String version) {
        checkNotNull(group, "Group cannot be null");
        checkNotNull(moduleName, "Module name cannot be null");
        checkNotNull(version, "Version cannot be null");
        addInclude(group, moduleName, version, false);
    }

    @Override
    public void includeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        checkNotNull(moduleNameRegex, "Module name cannot be null");
        checkNotNull(versionRegex, "Version cannot be null");
        addInclude(groupRegex, moduleNameRegex, versionRegex, true);
    }

    private void addInclude(String group, @Nullable String moduleName, @Nullable String version, boolean regex) {
        assertMutable();
        if (includeSpecs == null) {
            includeSpecs = Sets.newHashSet();
        }
        includeSpecs.add(new ContentSpec(regex, group, moduleName, version, versionSelectorScheme, versionSelectors, true));
    }

    @Override
    public void excludeGroup(String group) {
        checkNotNull(group, "Group cannot be null");
        addExclude(group, null, null, false);
    }

    @Override
    public void excludeGroupByRegex(String groupRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        addExclude(groupRegex, null, null, true);
    }

    @Override
    public void excludeModule(String group, String moduleName) {
        checkNotNull(group, "Group cannot be null");
        checkNotNull(moduleName, "Module name cannot be null");
        addExclude(group, moduleName, null, false);
    }

    @Override
    public void excludeModuleByRegex(String groupRegex, String moduleNameRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        checkNotNull(moduleNameRegex, "Module name cannot be null");
        addExclude(groupRegex, moduleNameRegex, null, true);
    }

    @Override
    public void excludeVersion(String group, String moduleName, String version) {
        checkNotNull(group, "Group cannot be null");
        checkNotNull(moduleName, "Module name cannot be null");
        checkNotNull(version, "Version cannot be null");
        addExclude(group, moduleName, version, false);
    }

    @Override
    public void excludeVersionByRegex(String groupRegex, String moduleNameRegex, String versionRegex) {
        checkNotNull(groupRegex, "Group cannot be null");
        checkNotNull(moduleNameRegex, "Module name cannot be null");
        checkNotNull(versionRegex, "Version cannot be null");
        addExclude(groupRegex, moduleNameRegex, versionRegex, true);
    }

    private void addExclude(String group, @Nullable String moduleName, @Nullable String version, boolean regex) {
        assertMutable();
        if (excludeSpecs == null) {
            excludeSpecs = Sets.newHashSet();
        }
        excludeSpecs.add(new ContentSpec(regex, group, moduleName, version, versionSelectorScheme, versionSelectors, false));
    }

    @Override
    public void onlyForConfigurations(String... configurationNames) {
        if (includedConfigurations == null) {
            includedConfigurations = Sets.newHashSet();
        }
        Collections.addAll(includedConfigurations, configurationNames);
    }

    @Override
    public void notForConfigurations(String... configurationNames) {
        if (excludedConfigurations == null) {
            excludedConfigurations = Sets.newHashSet();
        }
        Collections.addAll(excludedConfigurations, configurationNames);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void onlyForAttribute(Attribute<T> attribute, T... validValues) {
        if (requiredAttributes == null) {
            requiredAttributes = Maps.newHashMap();
        }
        requiredAttributes.put(Cast.uncheckedCast(attribute), ImmutableSet.copyOf(validValues));
    }

    Supplier<String> getRepositoryNameSupplier() {
        return repositoryNameSupplier;
    }

    @Nullable
    Set<String> getIncludedConfigurations() {
        return includedConfigurations;
    }

    void setIncludedConfigurations(@Nullable Set<String> includedConfigurations) {
        this.includedConfigurations = includedConfigurations;
    }

    @Nullable
    Set<String> getExcludedConfigurations() {
        return excludedConfigurations;
    }

    void setExcludedConfigurations(@Nullable Set<String> excludedConfigurations) {
        this.excludedConfigurations = excludedConfigurations;
    }

    @Nullable
    Set<ContentSpec> getIncludeSpecs() {
        return includeSpecs;
    }

    void setIncludeSpecs(@Nullable Set<ContentSpec> includeSpecs) {
        this.includeSpecs = includeSpecs;
    }

    @Nullable
    Set<ContentSpec> getExcludeSpecs() {
        return excludeSpecs;
    }

    void setExcludeSpecs(@Nullable Set<ContentSpec> excludeSpecs) {
        this.excludeSpecs = excludeSpecs;
    }

    @Nullable
    Map<Attribute<Object>, Set<Object>> getRequiredAttributes() {
        return requiredAttributes;
    }

    void setRequiredAttributes(@Nullable Map<Attribute<Object>, Set<Object>> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }

    private static class ContentSpec {
        private final boolean regex;
        private final String group;
        private final String module;
        private final String version;
        private final VersionSelectorScheme versionSelectorScheme;
        private final ConcurrentHashMap<String, VersionSelector> versionSelectors;
        private final boolean inclusive;
        private final int hashCode;

        private ContentSpec(boolean regex, String group, @Nullable String module, @Nullable String version, VersionSelectorScheme versionSelectorScheme, ConcurrentHashMap<String, VersionSelector> versionSelectors, boolean inclusive) {
            this.regex = regex;
            this.group = group;
            this.module = module;
            this.version = version;
            this.versionSelectorScheme = versionSelectorScheme;
            this.versionSelectors = versionSelectors;
            this.inclusive = inclusive;
            this.hashCode = Objects.hashCode(regex, group, module, version, inclusive);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ContentSpec that = (ContentSpec) o;
            return regex == that.regex &&
                    hashCode == that.hashCode &&
                    Objects.equal(group, that.group) &&
                    Objects.equal(module, that.module) &&
                    Objects.equal(version, that.version) &&
                    Objects.equal(inclusive, that.inclusive);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        SpecMatcher toMatcher() {
            if (regex) {
                return new PatternSpecMatcher(group, module, version, inclusive);
            }
            return new SimpleSpecMatcher(group, module, version, versionSelectorScheme, versionSelectors, inclusive);
        }
    }

    private interface SpecMatcher {
        boolean matches(ModuleIdentifier id);

        boolean matches(ModuleComponentIdentifier id);
    }

    private static class SimpleSpecMatcher implements SpecMatcher {
        private final String group;
        private final String module;
        private final String version;
        private final VersionSelector versionSelector;
        private final boolean inclusive;

        private SimpleSpecMatcher(String group, @Nullable String module, @Nullable String version, VersionSelectorScheme versionSelectorScheme, ConcurrentHashMap<String, VersionSelector> versionSelectors, boolean inclusive) {
            this.group = group;
            this.module = module;
            this.version = version;
            this.inclusive = inclusive;
            this.versionSelector = getVersionSelector(versionSelectors, versionSelectorScheme, version);
        }

        @Override
        public boolean matches(ModuleIdentifier id) {
            return group.equals(id.getGroup())
                && (module == null || module.equals(id.getName()))
                && (inclusive || version == null);
        }

        @Override
        public boolean matches(ModuleComponentIdentifier id) {
            return group.equals(id.getGroup())
                && (module == null || module.equals(id.getModule()))
                && (version == null || version.equals(id.getVersion()) || versionSelector.accept(id.getVersion()));
        }

        @Nullable
        private VersionSelector getVersionSelector(ConcurrentHashMap<String, VersionSelector> versionSelectors, VersionSelectorScheme versionSelectorScheme, @Nullable String version) {
            return version != null ? versionSelectors.computeIfAbsent(version, s -> versionSelectorScheme.parseSelector(version)) : null;
        }
    }

    private static class PatternSpecMatcher implements SpecMatcher {
        private final Pattern groupPattern;
        private final Pattern modulePattern;
        private final Pattern versionPattern;
        private final boolean inclusive;

        private PatternSpecMatcher(String group, @Nullable String module, @Nullable String version, boolean inclusive) {
            this.groupPattern = Pattern.compile(group);
            this.modulePattern = module == null ? null : Pattern.compile(module);
            this.versionPattern = version == null ? null : Pattern.compile(version);
            this.inclusive = inclusive;
        }

        @Override
        public boolean matches(ModuleIdentifier id) {
            return groupPattern.matcher(id.getGroup()).matches()
                && (modulePattern == null || modulePattern.matcher(id.getName()).matches())
                && (inclusive || versionPattern == null);
        }

        @Override
        public boolean matches(ModuleComponentIdentifier id) {
            return groupPattern.matcher(id.getGroup()).matches()
                && (modulePattern == null || modulePattern.matcher(id.getModule()).matches())
                && (versionPattern == null || versionPattern.matcher(id.getVersion()).matches());
        }
    }

    private class RepositoryFilterAction implements Action<ArtifactResolutionDetails> {
        private final ImmutableList<SpecMatcher> includeMatchers;
        private final ImmutableList<SpecMatcher> excludeMatchers;

        public RepositoryFilterAction(@Nullable ImmutableList<SpecMatcher> includeMatchers, @Nullable ImmutableList<SpecMatcher> excludeMatchers) {
            this.includeMatchers = includeMatchers;
            this.excludeMatchers = excludeMatchers;
        }

        @Override
        public void execute(ArtifactResolutionDetails details) {
            if (includedConfigurations != null && !includedConfigurations.contains(details.getConsumerName())) {
                details.notFound();
                return;
            }
            if (excludedConfigurations != null && excludedConfigurations.contains(details.getConsumerName())) {
                details.notFound();
                return;
            }
            if (includeMatchers != null && !anyMatch(includeMatchers, details)) {
                details.notFound();
                return;
            }
            if (excludeMatchers != null && anyMatch(excludeMatchers, details)) {
                details.notFound();
                return;
            }
            if (anyAttributesExcludes(details)) {
                details.notFound();
            }
        }

        private boolean anyAttributesExcludes(ArtifactResolutionDetails details) {
            if (requiredAttributes != null) {
                AttributeContainer consumerAttributes = details.getConsumerAttributes();
                for (Map.Entry<Attribute<Object>, Set<Object>> entry : requiredAttributes.entrySet()) {
                    Attribute<Object> key = entry.getKey();
                    Set<Object> allowedValues = entry.getValue();
                    Object value = consumerAttributes.getAttribute(key);
                    if (!allowedValues.contains(value)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean anyMatch(ImmutableList<SpecMatcher> matchers, ArtifactResolutionDetails details) {
            for (SpecMatcher matcher : matchers) {
                boolean matches;
                if (details.isVersionListing()) {
                    matches = matcher.matches(details.getModuleId());
                } else {
                    matches = matcher.matches(details.getComponentId());
                }
                if (matches) {
                    return true;
                }
            }
            return false;
        }
    }
}

