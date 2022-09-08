/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public final class NodeSets {

    public static NavigableSet<Node> newSortedNodeSet() {
        return new TreeSet<>(NodeComparator.INSTANCE);
    }

    public static List<Node> sortedListOf(Set<Node> nodes) {
        List<Node> sorted = new ArrayList<>(nodes);
        sorted.sort(NodeComparator.INSTANCE);
        return sorted;
    }

    private NodeSets() {
    }
}
