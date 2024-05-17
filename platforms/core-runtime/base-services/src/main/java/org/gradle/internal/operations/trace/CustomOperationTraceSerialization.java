/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.trace;

/**
 * Can be implemented by an operation details, result or progress object
 * to provide a custom form for serializing into the trace files.
 *
 * By default, objects are serialized using Groovy's reflective JSON serializer.
 */
public interface CustomOperationTraceSerialization {

    Object getCustomOperationTraceSerializableModel();

}
