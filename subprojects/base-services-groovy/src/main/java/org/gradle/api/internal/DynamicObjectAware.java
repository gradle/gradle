/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.internal.metaobject.DynamicObject;

/**
 * An object that can present a dynamic view of itself.
 *
 * The exposed dynamic object <i>may</i> provide functionality over and above what the type implementing
 * this interface can do. For example, the {@link DynamicObject} may provide the ability to register new
 * properties or implement methods that this object does not provide in a concrete way.
 */
public interface DynamicObjectAware {

    /**
     * Returns a {@link DynamicObject} for this object. This should include all static and dynamic properties and methods for this object.
     *
     * @return The dynamic object.
     */
    DynamicObject getAsDynamicObject();
}
