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

package org.gradle.internal.scan;

/**
 * This interface was used by scan plugin versions 1.6, 1.7 and 1.7.1.
 * These versions are no longer supported as of Gradle 4.0.
 * Therefore, any invocation of the collect* methods imply an unsupported version.
 * The current implementation will error accordingly.
 *
 * @since 3.4
 */
@UsedByScanPlugin("versions 1.6, 1.7 and 1.7.1")
public interface BuildScanRequest {

    void markRequested();

    void markDisabled();

    boolean collectRequested();

    boolean collectDisabled();
}
