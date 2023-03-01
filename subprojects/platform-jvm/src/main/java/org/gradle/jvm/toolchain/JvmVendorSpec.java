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

import org.gradle.api.Incubating;
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;

/**
 * Represents a filter for a vendor of a Java Virtual Machine implementation.
 *
 * @since 6.8
 */
public abstract class JvmVendorSpec {

    /**
     * A constant for using <a href="https://projects.eclipse.org/projects/adoptium">Eclipse Adoptium</a> as the JVM vendor.
     *
     * @since 7.4
     */
    public static final JvmVendorSpec ADOPTIUM = matching(KnownJvmVendor.ADOPTIUM);

    public static final JvmVendorSpec ADOPTOPENJDK = matching(KnownJvmVendor.ADOPTOPENJDK);

    public static final JvmVendorSpec AMAZON = matching(KnownJvmVendor.AMAZON);

    public static final JvmVendorSpec APPLE = matching(KnownJvmVendor.APPLE);

    public static final JvmVendorSpec AZUL = matching(KnownJvmVendor.AZUL);

    public static final JvmVendorSpec BELLSOFT = matching(KnownJvmVendor.BELLSOFT);

    /**
     * A constant for using <a href="https://www.graalvm.org/">GraalVM</a> as the JVM vendor.
     *
     * @since 7.1
     */
    public static final JvmVendorSpec GRAAL_VM = matching(KnownJvmVendor.GRAAL_VM);

    public static final JvmVendorSpec HEWLETT_PACKARD = matching(KnownJvmVendor.HEWLETT_PACKARD);

    public static final JvmVendorSpec IBM = matching(KnownJvmVendor.IBM);

    /**
     * A constant for using <a href="https://developer.ibm.com/languages/java/semeru-runtimes/">IBM Semeru Runtimes</a> as the JVM vendor.
     *
     * @since 7.4
     * @deprecated We are grouping all IBM runtimes under the '{@code IBM}' vendor, won't keep a separate constant for Semeru ones. Just use '{@code IBM}' instead.
     */
    @Deprecated
    public static final JvmVendorSpec IBM_SEMERU = IBM;

    /**
     * A constant for using <a href="https://www.microsoft.com/openjdk">Microsoft OpenJDK</a> as the JVM vendor.
     *
     * @since 7.3
     */
    public static final JvmVendorSpec MICROSOFT = matching(KnownJvmVendor.MICROSOFT);

    public static final JvmVendorSpec ORACLE = matching(KnownJvmVendor.ORACLE);

    public static final JvmVendorSpec SAP = matching(KnownJvmVendor.SAP);

    /**
     * Determines if the vendor passed as an argument matches this spec.
     * @param vendor the vendor to test
     * @return true if this spec matches the vendor
     *
     * @since 7.6
     */
    @Incubating
    public abstract boolean matches(String vendor);

    /**
     * Returns a vendor spec that matches a VM by its vendor.
     * <p>
     * A VM is determined eligible if the system property <code>java.vendor</code> contains
     * the given match string. The comparison is done case-insensitive.
     * </p>
     * @param match the sequence to search for
     * @return a new filter object
     */
    public static JvmVendorSpec matching(String match) {
        return DefaultJvmVendorSpec.matching(match);
    }

    private static JvmVendorSpec matching(KnownJvmVendor vendor) {
        return DefaultJvmVendorSpec.of(vendor);
    }

}
