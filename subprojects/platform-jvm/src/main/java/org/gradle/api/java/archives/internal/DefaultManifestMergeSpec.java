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
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.ManifestMergeDetails;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.*;

public class DefaultManifestMergeSpec implements ManifestMergeSpec {
    List<Object> mergePaths = new ArrayList<Object>();
    private final List<Action<? super ManifestMergeDetails>> actions = new ArrayList<Action<? super ManifestMergeDetails>>();

    public ManifestMergeSpec from(Object... mergePaths) {
        GUtil.flatten(mergePaths, this.mergePaths);
        return this;
    }

    public ManifestMergeSpec eachEntry(Action<? super ManifestMergeDetails> mergeAction) {
        actions.add(mergeAction);
        return this;
    }

    public ManifestMergeSpec eachEntry(Closure<?> mergeAction) {
        return eachEntry(new ClosureBackedAction<ManifestMergeDetails>(mergeAction));
    }

    public DefaultManifest merge(Manifest baseManifest, FileResolver fileResolver) {
        DefaultManifest mergedManifest = new DefaultManifest(fileResolver);
        mergedManifest.getAttributes().putAll(baseManifest.getAttributes());
        mergedManifest.getSections().putAll(baseManifest.getSections());
        for (Object mergePath : mergePaths) {
            DefaultManifest manifestToMerge = createManifest(mergePath, fileResolver);
            mergedManifest = mergeManifest(mergedManifest, manifestToMerge, fileResolver);
        }
        return mergedManifest;
    }

    private DefaultManifest mergeManifest(DefaultManifest baseManifest, DefaultManifest toMergeManifest, FileResolver fileResolver) {
        DefaultManifest mergedManifest = new DefaultManifest(fileResolver);
        mergeSection(null, mergedManifest, baseManifest.getAttributes(), toMergeManifest.getAttributes());
        Set<String> allSections = Sets.union(baseManifest.getSections().keySet(), toMergeManifest.getSections().keySet());
        for (String section : allSections) {
            mergeSection(section, mergedManifest,
                    GUtil.elvis(baseManifest.getSections().get(section), new DefaultAttributes()),
                    GUtil.elvis(toMergeManifest.getSections().get(section), new DefaultAttributes()));
        }
        return mergedManifest;
    }

    private void mergeSection(String section, DefaultManifest mergedManifest, Attributes baseAttributes, Attributes mergeAttributes) {
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
        String value = null;
        String baseValueString = baseValue != null ? baseValue.toString() : null;
        String mergeValueString = mergeValue != null ? mergeValue.toString() : null;
        value = mergeValueString == null ? baseValueString : mergeValueString; 
        return new DefaultManifestMergeDetails(section, key, baseValueString, mergeValueString, value);
    }

    private void addMergeDetailToManifest(String section, DefaultManifest mergedManifest, DefaultManifestMergeDetails mergeDetails) {
        if (!mergeDetails.isExcluded()) {
            if (section == null) {
                mergedManifest.attributes(WrapUtil.toMap(mergeDetails.getKey(), mergeDetails.getValue()));
            } else {
                mergedManifest.attributes(WrapUtil.toMap(mergeDetails.getKey(), mergeDetails.getValue()), section);
            }
        }
    }

    private DefaultManifest createManifest(Object mergePath, FileResolver fileResolver) {
        if (mergePath instanceof DefaultManifest) {
            return ((DefaultManifest) mergePath).getEffectiveManifest();
        }
        return new DefaultManifest(mergePath, fileResolver);
    }

    public List<Object> getMergePaths() {
        return mergePaths;
    }
}