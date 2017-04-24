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

package org.gradle.api.snapshotting.internal;

import org.gradle.api.snapshotting.SnapshotterConfiguration;
import org.gradle.api.tasks.PathSensitivity;

public class GenericSnapshotters {
    public static class Absolute implements SnapshotterConfiguration {}
    public static class Relative implements SnapshotterConfiguration {}
    public static class NameOnly implements SnapshotterConfiguration {}
    public static class None implements SnapshotterConfiguration {}
    public static class Output implements SnapshotterConfiguration {}

    public static Class<? extends SnapshotterConfiguration> valueOf(PathSensitivity sensitivity) {
        switch (sensitivity) {
            case ABSOLUTE:
                return Absolute.class;
            case RELATIVE:
                return Relative.class;
            case NAME_ONLY:
                return NameOnly.class;
            case NONE:
                return None.class;
            default:
                throw new AssertionError();
        }
    }
}
