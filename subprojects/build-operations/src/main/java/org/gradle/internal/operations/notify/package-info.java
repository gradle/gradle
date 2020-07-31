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

/**
 * This package forms the basic contract between the build scan plugin
 * and Gradle with regard to observing build operations.
 *
 * It has no dependencies to types outside of this package other than JDK types.
 *
 * Only binary compatible changes, from the caller's perspective,
 * can be made to these types.
 *
 * It can be assumed that only Gradle implements these interfaces,
 * except for {@link org.gradle.internal.operations.notify.BuildOperationNotificationListener}
 * which is implemented by the build scan plugin.
 */
@NonNullApi
package org.gradle.internal.operations.notify;

import org.gradle.api.NonNullApi;
