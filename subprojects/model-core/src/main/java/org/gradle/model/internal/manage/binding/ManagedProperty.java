/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.model.internal.manage.binding;

import org.gradle.model.internal.type.ModelType;

/**
 * A managed property of a struct type.
 *
 * <p>Managed properties are properties whose values need to be stored in child-nodes. A
 * managed property is detected when the view schemas declaring the struct declare a property with only abstract accessor
 * methods. This means that accessors declaring the property in the views should all be abstract, and the delegate type (if any)
 * should <em>not</em> provide an implementation for any of the property's accessor methods either.</p>
 *
 * <p>The managed property represents a property that is able to serve all its declaring view methods. For example, if the view
 * types declaring the struct only specify the property via a getter, then the managed property will be read-only. However, if
 * even one of the view types declare a setter for the property, it will be a read-write property. </p>
 *
 * <p>Different views can declare the same getter with different return types. In such a case the type of the managed property
 * will be the most specific of those return types (provided there is a single type that is assignable to any of the other
 * return types).</p>
 */
public class ManagedProperty<T> {
    private final String name;
    private final ModelType<T> type;
    private final boolean writable;
    private final boolean declaredAsHavingUnmanagedType;
    private final boolean internal;

    public ManagedProperty(String name, ModelType<T> type, boolean writable, boolean declaredAsHavingUnmanagedType, boolean internal) {
        this.name = name;
        this.type = type;
        this.writable = writable;
        this.declaredAsHavingUnmanagedType = declaredAsHavingUnmanagedType;
        this.internal = internal;
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return type;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isDeclaredAsHavingUnmanagedType() {
        return declaredAsHavingUnmanagedType;
    }

    @Override
    public String toString() {
        return name;
    }
}
