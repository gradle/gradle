/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package org.gradle.api.tasks.diagnostics.internal.repositories.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@NullMarked
public final class ReportContentFilter {
    public static final ReportContentFilter EMPTY = new ReportContentFilter(
        ImmutableList.of(), ImmutableList.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableMap.of()
    );

    private final List<String> includeRules;
    private final List<String> excludeRules;
    private final Set<String> onlyForConfigurations;
    private final Set<String> notForConfigurations;
    private final Map<String, Set<String>> onlyForAttributes;

    public ReportContentFilter(
        List<String> includeRules,
        List<String> excludeRules,
        Set<String> onlyForConfigurations,
        Set<String> notForConfigurations,
        Map<String, Set<String>> onlyForAttributes
    ) {
        this.includeRules = ImmutableList.copyOf(includeRules);
        this.excludeRules = ImmutableList.copyOf(excludeRules);
        this.onlyForConfigurations = ImmutableSet.copyOf(onlyForConfigurations);
        this.notForConfigurations = ImmutableSet.copyOf(notForConfigurations);
        ImmutableMap.Builder<String, Set<String>> b = ImmutableMap.builder();
        for (Map.Entry<String, Set<String>> e : onlyForAttributes.entrySet()) {
            b.put(e.getKey(), ImmutableSet.copyOf(e.getValue()));
        }
        this.onlyForAttributes = b.build();
    }

    public boolean isEmpty() {
        return includeRules.isEmpty()
            && excludeRules.isEmpty()
            && onlyForConfigurations.isEmpty()
            && notForConfigurations.isEmpty()
            && onlyForAttributes.isEmpty();
    }

    public List<String> getIncludeRules() {
        return includeRules;
    }

    public List<String> getExcludeRules() {
        return excludeRules;
    }

    public Set<String> getOnlyForConfigurations() {
        return onlyForConfigurations;
    }

    public Set<String> getNotForConfigurations() {
        return notForConfigurations;
    }

    public Map<String, Set<String>> getOnlyForAttributes() {
        return onlyForAttributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportContentFilter)) return false;
        ReportContentFilter that = (ReportContentFilter) o;
        return includeRules.equals(that.includeRules)
            && excludeRules.equals(that.excludeRules)
            && onlyForConfigurations.equals(that.onlyForConfigurations)
            && notForConfigurations.equals(that.notForConfigurations)
            && onlyForAttributes.equals(that.onlyForAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includeRules, excludeRules, onlyForConfigurations, notForConfigurations, onlyForAttributes);
    }
}
