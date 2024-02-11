/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain;


/**
 * Represents a filter for a implementation of a Java Virtual Machine.
 *
 * @since 6.8
 */
public final class JvmImplementation {

    /**
     * Vendor-specific virtual machine implementation.
     *
     * Acts as a placeholder and matches any implementation from any vendor.
     * Usually used to override specific implementation requests.
     */
    public static final JvmImplementation VENDOR_SPECIFIC = new JvmImplementation("vendor-specific");

    /**
     * Eclipse OpenJ9 (previously known as IBM J9) virtual machine implementation.
     *
     * Matches only virtual machine implementations using the OpenJ9/IBM J9 runtime engine.
     */
    public static final JvmImplementation J9 = new JvmImplementation("J9");

    private final String displayName;

    private JvmImplementation(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
