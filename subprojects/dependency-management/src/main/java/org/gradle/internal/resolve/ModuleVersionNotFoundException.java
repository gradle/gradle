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
package org.gradle.internal.resolve;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ModuleVersionNotFoundException extends ModuleVersionResolveException implements ResolutionProvider {
    private List<String> resolutions = ImmutableList.of();

    /**
     * This is used by {@link ModuleVersionResolveException#withIncomingPaths(java.util.Collection)}.
     */
    @SuppressWarnings("UnusedDeclaration")
    public ModuleVersionNotFoundException(ComponentSelector selector, Factory<String> message, Collection<String> resolutions) {
        super(selector, message);
        this.resolutions = ImmutableList.copyOf(resolutions);
    }


    public ModuleVersionNotFoundException(ModuleComponentSelector selector, Collection<String> attemptedLocations, Collection<String> unmatchedVersions, Collection<RejectedVersion> rejectedVersions) {
        super(selector, format(selector, attemptedLocations, unmatchedVersions, rejectedVersions));
        recordPossibleResolution(attemptedLocations);
    }

    public ModuleVersionNotFoundException(ModuleVersionIdentifier id, Collection<String> attemptedLocations) {
        super(id, format(id, attemptedLocations));
        recordPossibleResolution(attemptedLocations);
    }

    public ModuleVersionNotFoundException(ModuleComponentSelector selector, Collection<String> attemptedLocations) {
        super(selector, format(selector, attemptedLocations));
        recordPossibleResolution(attemptedLocations);
    }

    private static Factory<String> format(ModuleComponentSelector selector, Collection<String> locations, Collection<String> unmatchedVersions, Collection<RejectedVersion> rejectedVersions) {
        return () -> {
            TreeFormatter builder = new TreeFormatter();
            if (unmatchedVersions.isEmpty() && rejectedVersions.isEmpty()) {
                builder.node(String.format("Could not find any matches for %s as no versions of %s:%s are available.", selector, selector.getGroup(), selector.getModule()));
            } else {
                builder.node(String.format("Could not find any version that matches %s.", selector));
                if (!unmatchedVersions.isEmpty()) {
                    builder.node("Versions that do not match");
                    appendSizeLimited(builder, unmatchedVersions);
                }
                if (!rejectedVersions.isEmpty()) {
                    Collection<RejectedVersion> byRule = Lists.newArrayListWithExpectedSize(rejectedVersions.size());
                    Collection<RejectedVersion> byAttributes = Lists.newArrayListWithExpectedSize(rejectedVersions.size());
                    mapRejections(rejectedVersions, byRule, byAttributes);
                    if (!byRule.isEmpty()) {
                        builder.node("Versions rejected by component selection rules");
                        appendSizeLimited(builder, byRule);
                    }
                    if (!byAttributes.isEmpty()) {
                        builder.node("Versions rejected by attribute matching");
                        appendSizeLimited(builder, byAttributes);
                    }
                }
            }
            addLocations(builder, locations);
            return builder.toString();
        };
    }

    private static void mapRejections(Collection<RejectedVersion> in, Collection<RejectedVersion> outByRule, Collection<RejectedVersion> outByAttributes) {
        for (RejectedVersion version : in) {
            if (version instanceof RejectedByAttributesVersion) {
                outByAttributes.add(version);
            } else {
                outByRule.add(version);
            }
        }
    }

    private static Factory<String> format(ModuleVersionIdentifier id, Collection<String> locations) {
        return () -> {
            TreeFormatter builder = new TreeFormatter();
            builder.node(String.format("Could not find %s.", id));
            addLocations(builder, locations);
            return builder.toString();
        };
    }

    private static Factory<String> format(ModuleComponentSelector selector, Collection<String> locations) {
        return () -> {
            TreeFormatter builder = new TreeFormatter();
            builder.node(String.format("Could not find any version that matches %s.", selector));
            addLocations(builder, locations);
            return builder.toString();
        };
    }

    private static void appendSizeLimited(TreeFormatter builder, Collection<?> values) {
        builder.startChildren();
        Iterator<?> iterator = values.iterator();
        int count = Math.min(5, values.size());
        for (int i = 0; i < count; i++) {
            Object next = iterator.next();
            if (next instanceof RejectedVersion) {
                ((RejectedVersion) next).describeTo(builder);
            } else {
                builder.node(next.toString());
            }
        }
        if (count < values.size()) {
            builder.node(String.format("+ %d more", values.size() - count));
        }
        builder.endChildren();
    }

    private static void addLocations(TreeFormatter builder, Collection<String> locations) {
        if (locations.isEmpty()) {
            return;
        }
        builder.node("Searched in the following locations");
        builder.startChildren();

        locations.forEach(builder::node);

        builder.endChildren();
    }

    /**
     * This method should ideally use more data to figure out if the message should be displayed
     * or not. In particular, the ivy patterns can make it difficult to find out if an Ivy artifact
     * source should be configured. At this stage, this information is lost, so we do a best effort
     * based on the file locations.
     */
    private void recordPossibleResolution(Collection<String> locations) {
        if (locations.size() == 1) {
            String singleLocation = locations.iterator().next();
            String format = getFormatName(singleLocation);
            if (format != null) {
                resolutions = ImmutableList.of(String.format("If the artifact you are trying to retrieve can be found in the repository but without metadata in '%s' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.", format));
            }
        }
    }

    private static String getFormatName(String singleLocation) {
        boolean isPom = singleLocation.endsWith(".pom");
        boolean isIvy = singleLocation.contains("ivy-") && singleLocation.endsWith(".xml");
        boolean isModule = singleLocation.endsWith(".module");
        return isPom ? "Maven POM" : (isIvy ? "ivy.xml" : (isModule ? "Gradle module" : null));
    }

    @Override
    @Nonnull
    public List<String> getResolutions() {
        return resolutions;
    }

    protected ModuleVersionResolveException createCopy() {
        try {
            String message = getMessage();
            return getClass().getConstructor(ComponentSelector.class, Factory.class, Collection.class).newInstance(getSelector(), (Factory<String>) () -> message, resolutions);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
