/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.specs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
abstract public class CompositeSpec<T> implements Spec<T> {
    private List<Spec> specs;

    protected CompositeSpec(Spec... specs) {
        this.specs = Arrays.asList(specs);
    }

    public List<Spec> getSpecs() {
        return Collections.unmodifiableList(specs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompositeSpec)) return false;

        CompositeSpec that = (CompositeSpec) o;

        if (specs != null ? !specs.equals(that.specs) : that.specs != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return specs != null ? specs.hashCode() : 0;
    }
}
