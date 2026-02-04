/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect.bench;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

record SetFixture(Random random, List<Object> present, List<Object> absent) {

    public static SetFixture of(int size) {
        Random random = new Random(1234);

        List<Object> present = new ArrayList<>(size);
        List<Object> absent = new ArrayList<>(size);

        IntOpenHashSet presentSet = new IntOpenHashSet(size);
        while (present.size() < size) {
            int next = random.nextInt();
            if (presentSet.add(next)) {
                present.add(next);
            }
        }

        IntOpenHashSet absentSet = new IntOpenHashSet(size);
        while (absent.size() < size) {
            int next = random.nextInt();
            if (!presentSet.contains(next) && absentSet.add(next)) {
                absent.add(next);
            }
        }

        return new SetFixture(random, present, absent);
    }

    public Object randomAbsent() {
        return randomElementOf(absent);
    }

    public Object randomPresent() {
        return randomElementOf(present);
    }

    private Object randomElementOf(List<Object> list) {
        return list.get(random.nextInt(list.size()));
    }
}
