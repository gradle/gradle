/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.internal.xcodeproj;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A collection of files in Xcode's virtual filesystem hierarchy.
 */
public class PBXGroup extends PBXReference {
    private final List<PBXReference> children;
    private final LoadingCache<String, PBXGroup> childGroupsByName;

    // Unfortunately, we can't determine this at constructor time, because CacheBuilder
    // calls our constructor and it's not easy to pass arguments to it.
    private SortPolicy sortPolicy;
    public PBXGroup(String name, @Nullable String path, SourceTree sourceTree) {
        super(name, path, sourceTree);

        sortPolicy = SortPolicy.BY_NAME;
        children = Lists.newArrayList();

        childGroupsByName = CacheBuilder.newBuilder().build(
            new CacheLoader<String, PBXGroup>() {
                @Override
                public PBXGroup load(String key) throws Exception {
                    PBXGroup group = new PBXGroup(key, null, SourceTree.GROUP);
                    children.add(group);
                    return group;
                }
            });
    }

    public PBXGroup getOrCreateChildGroupByName(String name) {
        return childGroupsByName.getUnchecked(name);
    }

    public List<PBXReference> getChildren() {
        return children;
    }

    public SortPolicy getSortPolicy() {
        return sortPolicy;
    }

    public void setSortPolicy(SortPolicy sortPolicy) {
        this.sortPolicy = Preconditions.checkNotNull(sortPolicy);
    }

    @Override
    public String isa() {
        return "PBXGroup";
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        if (sortPolicy == SortPolicy.BY_NAME) {
            Collections.sort(children, new Comparator<PBXReference>() {
                @Override
                public int compare(PBXReference o1, PBXReference o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        }

        s.addField("children", children);
    }

    /**
     * Method by which group contents will be sorted.
     */
    public enum SortPolicy {
        /**
         * By name, in default Java sort order.
         */
        BY_NAME,

        /**
         * Group contents will not be sorted, and will remain in the
         * order they were added.
         */
        UNSORTED;
    }
}
