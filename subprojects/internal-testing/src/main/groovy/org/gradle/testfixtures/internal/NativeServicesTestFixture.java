/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.testfixtures.internal;

import org.gradle.internal.nativeintegration.services.NativeServices;

import java.io.File;

public class NativeServicesTestFixture {
    static NativeServices nativeServices;
    static boolean initialized;

    public static void initialize() {
        if (!initialized) {
            File nativeDir = new File(System.getProperty("integTest.gradleUserHomeDir", "build/user-home"), "worker-1/test-fixtures");
            NativeServices.initialize(nativeDir);
        }
    }

    public static NativeServices getInstance() {
        if (nativeServices == null) {
            initialize();
            nativeServices = NativeServices.getInstance();
        }
        return nativeServices;
    }
}
