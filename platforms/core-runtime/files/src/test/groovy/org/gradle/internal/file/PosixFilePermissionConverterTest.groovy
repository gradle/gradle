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

package org.gradle.internal.file

import org.gradle.internal.file.nio.PosixFilePermissionConverter
import spock.lang.Specification

import java.nio.file.attribute.PosixFilePermission

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE

class PosixFilePermissionConverterTest extends Specification {
    def "converts Set<PosixFilePermission to int representation"() {

        expect:
        PosixFilePermissionConverter.convertToInt(perms) == intValue

        where:
        perms                                                                |       intValue
        EnumSet.noneOf(PosixFilePermission)                                  |       0
        EnumSet.allOf(PosixFilePermission)                                   |       0777
        EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)                   |       0700
        EnumSet.of(OWNER_READ, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE)       |       0470
        EnumSet.of(OWNER_READ, GROUP_READ, OTHERS_READ)                      |       0444
        EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)         |       0644
        EnumSet.of(OWNER_READ, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE)       |       0560
    }


    def "converts int representation to Set<PosixFilePermission)String representation"() {
        expect:
        perms == PosixFilePermissionConverter.convertToPermissionsSet(intValue)

        where:
        perms                                                                |       intValue
        EnumSet.noneOf(PosixFilePermission)                                  |       0
        EnumSet.allOf(PosixFilePermission)                                   |       0777
        EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)                   |       0700
        EnumSet.of(OWNER_READ, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE)       |       0470
        EnumSet.of(OWNER_READ, GROUP_READ, OTHERS_READ)                      |       0444
        EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)         |       0644
        EnumSet.of(OWNER_READ, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE)       |       0560

    }
}
