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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

public class ManagedValueSnapshot extends AbstractManagedValueSnapshot<ValueSnapshot> implements ValueSnapshot {
    private final String className;

    public ManagedValueSnapshot(String className, ValueSnapshot state) {
        super(state);
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        ManagedValueSnapshot other = (ManagedValueSnapshot) obj;
        return className.equals(other.className);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ className.hashCode();
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (snapshot.equals(this)) {
            return this;
        }
        return snapshot;
    }
}
