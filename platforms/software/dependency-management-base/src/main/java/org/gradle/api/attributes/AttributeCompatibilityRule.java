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

package org.gradle.api.attributes;

import org.gradle.api.Action;

/**
 * A rule that determines whether a given attribute value is compatible some provided attribute value.
 * <p>
 * The provided {@link CompatibilityCheckDetails} will give access to consumer and producer values and allow implementation
 * mark the producer value as compatible or not.
 * <p>
 * Note that the rule will never receive a {@code CompatibilityCheckDetails} that has {@code equal} consumer and producer
 * values as this check is performed before invoking the rule and assumes compatibility in that case.
 *
 * @since 4.0
 * @param <T> The attribute value type.
 * @see CompatibilityCheckDetails
 */
public interface AttributeCompatibilityRule<T> extends Action<CompatibilityCheckDetails<T>> {
}
