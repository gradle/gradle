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
package org.gradle.api.attributes;

import org.gradle.api.Incubating;

/**
 * <p>A compatibility rule can tell if two {@link AttributeValue values} are compatible.
 * Compatibility doesn't mean equality. Typically two different Java platforms can be
 * compatible, without being equal.</p>
 *
 * <p>A rule <i>can</i> express an opinion by calling the @{link {@link CompatibilityCheckDetails#compatible()}}
 * method to tell that two attributes are compatible, or it <i>can</i> call {@link CompatibilityCheckDetails#incompatible()}
 * to say that they are not compatible. It is not mandatory for a rule to express an opinion.</p>
 *
 * @param <T> the type of the attribute
 */
@Incubating
public interface CompatibilityRule<T> {
    /**
     * Called whenever we need to check if a value from a consumer is compatible with the value from a
     * producer. Both the consumer and producer values can be present, missing or unknown.
     *
     * @param details the check details
     */
    void checkCompatibility(CompatibilityCheckDetails<T> details);
}
