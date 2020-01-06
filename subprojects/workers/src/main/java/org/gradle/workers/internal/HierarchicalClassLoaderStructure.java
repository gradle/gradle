/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.base.Objects;
import org.gradle.internal.classloader.ClassLoaderSpec;

public class HierarchicalClassLoaderStructure implements ClassLoaderStructure {
    private final ClassLoaderSpec self;
    private final HierarchicalClassLoaderStructure parent;

    public HierarchicalClassLoaderStructure(ClassLoaderSpec self) {
        this(self, null);
    }

    public HierarchicalClassLoaderStructure(ClassLoaderSpec self, HierarchicalClassLoaderStructure parent) {
        this.self = self;
        this.parent = parent;
    }

    public HierarchicalClassLoaderStructure withChild(ClassLoaderSpec spec) {
        HierarchicalClassLoaderStructure childNode = new HierarchicalClassLoaderStructure(spec, this);
        return childNode;
    }

    @Override
    public ClassLoaderSpec getSpec() {
        return self;
    }

    @Override
    public HierarchicalClassLoaderStructure getParent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HierarchicalClassLoaderStructure that = (HierarchicalClassLoaderStructure) o;
        return Objects.equal(self, that.self) &&
                Objects.equal(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(self, parent);
    }

    @Override
    public String toString() {
        return "HierarchicalClassLoaderStructure{" +
                "self=" + self +
                ", parent=" + parent +
                '}';
    }
}
