/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks.util.internal;

import org.gradle.api.Action;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;

public class IntersectionPatternSet extends PatternSet {

    private final PatternSet other;

    public IntersectionPatternSet(PatternSet other) {
        super(other);
        this.other = other;
    }

    public PatternSet getOther() {
        return other;
    }

    @Override
    public Spec<FileTreeElement> getAsSpec() {
        return Specs.intersect(super.getAsSpec(), other.getAsSpec());
    }

    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {
        return PatternSetAntBuilderDelegate.and(node, new Action<Object>() {
            @Override
            public void execute(Object andNode) {
                org.gradle.api.tasks.util.internal.IntersectionPatternSet.super.addToAntBuilder(andNode, null);
                other.addToAntBuilder(andNode, null);
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return other.isEmpty() && super.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        org.gradle.api.tasks.util.internal.IntersectionPatternSet that = (org.gradle.api.tasks.util.internal.IntersectionPatternSet) o;

        return other != null ? other.equals(that.other) : that.other == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (other != null ? other.hashCode() : 0);
        return result;
    }
}
