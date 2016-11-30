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
 * <p>An ordered compatibility rule will use the provided {@link java.util.Comparator} to compare values. It will
 * only take a decision if both the consumer and producer attributes are present.</p>
 *
 * <p>A provider attribute is compatible with the consumer attribute if it's lower than or equal to the consumer value.</p>
 *
 * @param <T>
 */
@Incubating
public interface OrderedCompatibilityRule<T> extends CompatibilityRule<T> {
}
