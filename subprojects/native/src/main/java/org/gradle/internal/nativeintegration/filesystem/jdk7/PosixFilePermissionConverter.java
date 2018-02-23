/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem.jdk7;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

@SuppressWarnings("Since15")
public class PosixFilePermissionConverter {

    public static Set<PosixFilePermission> convertToPermissionsSet(int mode) {
        Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);

        if (isSet(mode, 0400)) {
            result.add(OWNER_READ);
        }
        if (isSet(mode, 0200)) {
            result.add(OWNER_WRITE);
        }
        if (isSet(mode, 0100)) {
            result.add(OWNER_EXECUTE);
        }

        if (isSet(mode, 040)) {
            result.add(GROUP_READ);
        }
        if (isSet(mode, 020)) {
            result.add(GROUP_WRITE);
        }
        if (isSet(mode, 010)) {
            result.add(GROUP_EXECUTE);
        }
        if (isSet(mode, 04)) {
            result.add(OTHERS_READ);
        }
        if (isSet(mode, 02)) {
            result.add(OTHERS_WRITE);
        }
        if (isSet(mode, 01)) {
            result.add(OTHERS_EXECUTE);
        }
        return result;
    }

    private static boolean isSet(int mode, int testbit) {
        return (mode & testbit) == testbit;
    }

    public static int convertToInt(Set<PosixFilePermission> permissions) {
        int result = 0;
        if (permissions.contains(OWNER_READ)) {
            result = result | 0400;
        }
        if (permissions.contains(OWNER_WRITE)) {
            result = result | 0200;
        }
        if (permissions.contains(OWNER_EXECUTE)) {
            result = result | 0100;
        }
        if (permissions.contains(GROUP_READ)) {
            result = result | 040;
        }
        if (permissions.contains(GROUP_WRITE)) {
            result = result | 020;
        }
        if (permissions.contains(GROUP_EXECUTE)) {
            result = result | 010;
        }
        if (permissions.contains(OTHERS_READ)) {
            result = result | 04;
        }
        if (permissions.contains(OTHERS_WRITE)) {
            result = result | 02;
        }
        if (permissions.contains(OTHERS_EXECUTE)) {
            result = result | 01;
        }
        return result;
    }
}
