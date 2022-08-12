/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.java.archives.internal;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.ManifestMergeDetails;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.WrapUtil;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultManifestMergeSpec implements ManifestMergeSpec {
    List<Object> mergePaths = new ArrayList<Object>();
    private final List<Action<? super ManifestMergeDetails>> actions = new ArrayList<Action<? super ManifestMergeDetails>>();
    private String contentCharset = DefaultManifest.DEFAULT_CONTENT_CHARSET;

    @Override
    public String getContentCharset() {
        return this.contentCharset;
    }

    @Override
    public void setContentCharset(String contentCharset) {
        if (contentCharset == null) {
            throw new InvalidUserDataException("contentCharset must not be null");
        }
        if (!Charset.isSupported(contentCharset)) {
            throw new InvalidUserDataException(String.format("Charset for contentCharset '%s' is not supported by your JVM", contentCharset));
        }
        this.contentCharset = contentCharset;
    }

    @Override
    public ManifestMergeSpec from(Object... mergePaths) {
        GUtil.flatten(mergePaths, this.mergePaths);
        return this;
    }

    @Override
    public ManifestMergeSpec eachEntry(Action<? super ManifestMergeDetails> mergeAction) {
        actions.add(mergeAction);
        return this;
    }

    @Override
    public ManifestMergeSpec eachEntry(Closure<?> mergeAction) {
        return eachEntry(ConfigureUtil.configureUsing(mergeAction));
    }

    public DefaultManifest merge(Manifest baseManifest, PathToFileResolver fileResolver) {
        String baseContentCharset = baseManifest instanceof ManifestInternal ? ((ManifestInternal) baseManifest).getContentCharset() : DefaultManifest.DEFAULT_CONTENT_CHARSET;
        DefaultManifest mergedManifest = new DefaultManifest(fileResolver, baseContentCharset);
        mergedManifest.getAttributes().putAll(baseManifest.getAttributes());
        mergedManifest.getSections().putAll(baseManifest.getSections());
        for (Object mergePath : mergePaths) {
            Manifest manifestToMerge = createManifest(mergePath, fileResolver, contentCharset);
            mergedManifest = mergeManifest(mergedManifest, manifestToMerge, fileResolver);
        }
        return mergedManifest;
    }

    private DefaultManifest mergeManifest(Manifest baseManifest, Manifest toMergeManifest, PathToFileResolver fileResolver) {
        DefaultManifest mergedManifest = new DefaultManifest(fileResolver);
        mergeSection(null, mergedManifest, baseManifest.getAttributes(), toMergeManifest.getAttributes());
        Set<String> allSections = Sets.union(baseManifest.getSections().keySet(), toMergeManifest.getSections().keySet());
        for (String section : allSections) {
            mergeSection(section, mergedManifest,
                    GUtil.getOrDefault(baseManifest.getSections().get(section), DefaultAttributes::new),
                    GUtil.getOrDefault(toMergeManifest.getSections().get(section), DefaultAttributes::new));
        }
        return mergedManifest;
    }

    private void mergeSection(String section, Manifest mergedManifest, Attributes baseAttributes, Attributes mergeAttributes) {
        Map<String, Object> mergeOnlyAttributes = new LinkedHashMap<String, Object>(mergeAttributes);
        Set<DefaultManifestMergeDetails> mergeDetailsSet = new LinkedHashSet<DefaultManifestMergeDetails>();

        for (Map.Entry<String, Object> baseEntry : baseAttributes.entrySet()) {
            Object mergeValue = mergeAttributes.get(baseEntry.getKey());
            mergeDetailsSet.add(getMergeDetails(section, baseEntry.getKey(), baseEntry.getValue(), mergeValue));
            mergeOnlyAttributes.remove(baseEntry.getKey());
        }
        for (Map.Entry<String, Object> mergeEntry : mergeOnlyAttributes.entrySet()) {
            mergeDetailsSet.add(getMergeDetails(section, mergeEntry.getKey(), null, mergeEntry.getValue()));
        }

        for (DefaultManifestMergeDetails mergeDetails : mergeDetailsSet) {
            for (Action<? super ManifestMergeDetails> action : actions) {
                action.execute(mergeDetails);
            }
            addMergeDetailToManifest(section, mergedManifest, mergeDetails);
        }
    }

    private DefaultManifestMergeDetails getMergeDetails(String section, String key, Object baseValue, Object mergeValue) {
        String baseValueString = resolveValueToString(baseValue);
        String mergeValueString = resolveValueToString(mergeValue);
        String value = mergeValueString == null ? baseValueString : mergeValueString;
        return new DefaultManifestMergeDetails(section, key, baseValueString, mergeValueString, value);
    }

    private static String resolveValueToString(Object value) {
        if (value == null) {
            return null;
        } else if (value instanceof Provider) {
            Object providedValue = ((Provider<?>) value).getOrNull();
            return resolveValueToString(providedValue);
        } else {
            return value.toString();
        }
    }

    private void addMergeDetailToManifest(String section, Manifest mergedManifest, DefaultManifestMergeDetails mergeDetails) {
        if (!mergeDetails.isExcluded()) {
            if (section == null) {
                mergedManifest.attributes(WrapUtil.toMap(mergeDetails.getKey(), mergeDetails.getValue()));
            } else {
                mergedManifest.attributes(WrapUtil.toMap(mergeDetails.getKey(), mergeDetails.getValue()), section);
            }
        }
    }

    private Manifest createManifest(Object mergePath, PathToFileResolver fileResolver, String contentCharset) {
        if (mergePath instanceof Manifest) {
            return ((Manifest) mergePath).getEffectiveManifest();
        }
        return new DefaultManifest(mergePath, fileResolver, contentCharset);
    }

    public List<Object> getMergePaths() {
        return mergePaths;
    }
}
