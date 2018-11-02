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
import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class DefaultRepositoryContentDescriptor implements RepositoryContentDescriptorInternal {
    private Set<String> includedConfigurations;
    private Set<String> excludedConfigurations;
    private Set<ContentSpec> specs;
    private Map<Attribute<Object>, Set<Object>> requiredAttributes;
    private Mode mode;

    private Action<? super ArtifactResolutionDetails> cachedAction;

    private void switchTo(Mode mode) {
        if (cachedAction != null) {
            throw new IllegalStateException("Cannot mutate content repository descriptor after repository has been used");
        }
        if (this.mode != mode) {
            this.mode = mode;
            this.specs = Sets.newHashSet();
        }
    }

    @Override
    public Action<? super ArtifactResolutionDetails> toContentFilter() {
        if (cachedAction != null) {
            return cachedAction;
        }
        if (includedConfigurations == null && excludedConfigurations == null && specs == null && requiredAttributes == null) {
            // no filtering in place
            return null;
        }
        ImmutableList<SpecMatcher> matchers = createSpecMatchers();
        cachedAction = new RepositoryFilterAction(matchers);
        return cachedAction;
    }

    private ImmutableList<SpecMatcher> createSpecMatchers() {
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
        assert group != null : "Group cannot be null";
        addInclude(group, null, null);
    }


    @Override
    public void includeModule(String group, String moduleName) {
        assert group != null : "Group cannot be null";
        assert moduleName != null : "Module name cannot be null";
        addInclude(group, moduleName, null);
    }

    @Override
    public void includeVersion(String group, String moduleName, String version) {
        assert group != null : "Group cannot be null";
        assert moduleName != null : "Module name cannot be null";
        assert version != null : "Version cannot be null";
        addInclude(group, moduleName, version);
    }

    private void addInclude(String group, String moduleName, String version) {
        switchTo(Mode.include);
        specs.add(new ContentSpec(false, group, moduleName, version));
    }

    @Override
    public void excludeGroup(String group) {
        assert group != null : "Group cannot be null";
        addExclude(group, null, null);
    }

    @Override
    public void excludeModule(String group, String moduleName) {
        assert group != null : "Group cannot be null";
        assert moduleName != null : "Module name cannot be null";
        addExclude(group, moduleName, null);
    }

    @Override
    public void excludeVersion(String group, String moduleName, String version) {
        assert group != null : "Group cannot be null";
        assert moduleName != null : "Module name cannot be null";
        assert version != null : "Version cannot be null";
        addExclude(group, moduleName, version);
    }

    private void addExclude(String group, String moduleName, String version) {
        switchTo(Mode.exclude);
        specs.add(new ContentSpec(false, group, moduleName, version));
    }

    @Override
    public void onlyForConfigurations(String... configurationNames) {
        if (includedConfigurations == null) {
            includedConfigurations = Sets.newHashSet();
        }
        Collections.addAll(includedConfigurations, configurationNames);
    }

    @Override
    public void excludeConfigurations(String... configurationNames) {
        if (excludedConfigurations == null) {
            excludedConfigurations = Sets.newHashSet();
        }
        Collections.addAll(excludedConfigurations, configurationNames);
    }

    @Override
    public <T> void requiresAttribute(Attribute<T> attribute, T... validValues) {
        if (requiredAttributes == null) {
            requiredAttributes = Maps.newHashMap();
        }
        requiredAttributes.put(Cast.uncheckedCast(attribute), ImmutableSet.copyOf(validValues));
    }

    enum Mode {
        include,
        exclude;

        boolean notFound(boolean matches) {
            switch (this) {
                case include:
                    return !matches;
                case exclude:
                    return matches;
            }
            return false;
        }
    }

    private static class ContentSpec {
        private final boolean regex;
        private final String group;
        private final String module;
        private final String version;
        private final int hashCode;

        private ContentSpec(boolean regex, String group, String module, String version) {
            this.regex = regex;
            this.group = group;
            this.module = module;
            this.version = version;
            this.hashCode = Objects.hashCode(regex, group, module, version);
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
                    Objects.equal(version, that.version);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        SpecMatcher toMatcher() {
            if (regex) {
                return new PatternSpecMatcher(group, module, version);
            }
            return new SimpleSpecMatcher(group, module, version);
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

        private SimpleSpecMatcher(String group, String module, String version) {
            this.group = group;
            this.module = module;
            this.version = version;
        }

        @Override
        public boolean matches(ModuleIdentifier id) {
            return group.equals(id.getGroup())
                    && (module == null || module.equals(id.getName()));
        }

        @Override
        public boolean matches(ModuleComponentIdentifier id) {
            return group.equals(id.getGroup())
                    && (module == null || module.equals(id.getModule()))
                    && (version == null || version.equals(id.getVersion()));
        }
    }

    private static class PatternSpecMatcher implements SpecMatcher {
        private final Pattern groupPattern;
        private final Pattern modulePattern;
        private final Pattern versionPattern;

        private PatternSpecMatcher(String group, String module, String version) {
            this.groupPattern = Pattern.compile(group);
            this.modulePattern = module == null ? null : Pattern.compile(module);
            this.versionPattern = version == null ? null : Pattern.compile(version);
        }

        @Override
        public boolean matches(ModuleIdentifier id) {
            return groupPattern.matcher(id.getGroup()).matches()
                    && (modulePattern == null || modulePattern.matcher(id.getName()).matches());
        }

        @Override
        public boolean matches(ModuleComponentIdentifier id) {
            return groupPattern.matcher(id.getGroup()).matches()
                    && (modulePattern == null || modulePattern.matcher(id.getModule()).matches())
                    && (versionPattern == null || versionPattern.matcher(id.getVersion()).matches());
        }
    }

    private class RepositoryFilterAction implements Action<ArtifactResolutionDetails> {
        private final ImmutableList<SpecMatcher> matchers;

        public RepositoryFilterAction(ImmutableList<SpecMatcher> matchers) {
            this.matchers = matchers;
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
            if (anyMatcherExcludes(details)) {
                return;
            }
            if (anyAttributesExcludes(details)) {
                return;
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
                        details.notFound();
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean anyMatcherExcludes(ArtifactResolutionDetails details) {
            if (matchers != null) {
                for (SpecMatcher matcher : matchers) {
                    boolean matches;
                    if (details.isVersionListing()) {
                        matches = matcher.matches(details.getModuleId());
                    } else {
                        matches = matcher.matches(details.getComponentId());
                    }
                    if (mode.notFound(matches)) {
                        details.notFound();
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
