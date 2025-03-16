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

package org.gradle.internal.operations.trace;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

public class BuildOperationTree {

    public final List<BuildOperationRecord> roots;
    public final Map<Long, BuildOperationRecord> records;

    BuildOperationTree(List<BuildOperationRecord> roots) {
        ImmutableMap.Builder<Long, BuildOperationRecord> records = ImmutableMap.builder();
        for (BuildOperationRecord record : roots) {
            visit(records, record);
        }
        this.roots = BuildOperationRecord.ORDERING.immutableSortedCopy(roots);
        this.records = records.build();
    }

    private void visit(ImmutableMap.Builder<Long, BuildOperationRecord> records, BuildOperationRecord record) {
        records.put(record.id, record);
        for (BuildOperationRecord child : record.children) {
            visit(records, child);
        }
    }

    static List<Map<String, ?>> serialize(List<BuildOperationRecord> roots) {
        return Lists.transform(roots, new Function<BuildOperationRecord, Map<String, ?>>() {
            @Override
            public Map<String, ?> apply(BuildOperationRecord input) {
                return input.toSerializable();
            }
        });
    }

}
