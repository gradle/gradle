/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.nativecode.base.internal.DefaultFlavor;

/**
 * Defines a custom variant that can be built for a {@link NativeComponent}.
 */
@Incubating
public interface Flavor extends Named {
    Flavor DEFAULT = new DefaultFlavor("default", true);

    /**
     * Is this the automatically created default flavor for the component?
     */
    boolean isDefault();
}
