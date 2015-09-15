/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

/**
 * Model schema with managed implementation. This means that we have control over the actual implementation type for the type
 * described in the schema, and we also control the instantiation of managed view instances.
 *
 * @param <T> the type the schema is extracted from.
 */
public interface ManagedImplModelSchema<T> extends ModelSchema<T> {
}
