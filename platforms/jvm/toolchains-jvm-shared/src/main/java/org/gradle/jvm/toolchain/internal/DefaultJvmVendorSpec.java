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

package org.gradle.jvm.toolchain.internal;

import com.google.common.base.Objects;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.util.function.Predicate;

public class DefaultJvmVendorSpec extends JvmVendorSpec implements Predicate<JvmInstallationMetadata> {

    private static final JvmVendorSpec ANY = new DefaultJvmVendorSpec(v -> true, "any vendor");

    private final Predicate<JvmVendor> matcher;
    private final String description;

    public static JvmVendorSpec matching(String match) {
        return new DefaultJvmVendorSpec(vendor -> StringUtils.containsIgnoreCase(vendor.getRawVendor(), match), "vendor matching('" + match + "')");
    }

    public static JvmVendorSpec of(JvmVendor.KnownJvmVendor knownVendor) {
        return new DefaultJvmVendorSpec(vendor -> vendor.getKnownVendor() == knownVendor, knownVendor.asJvmVendor().getDisplayName());
    }

    public static JvmVendorSpec any() {
        return ANY;
    }

    private DefaultJvmVendorSpec(Predicate<JvmVendor> predicate, String description) {
        this.matcher = predicate;
        this.description = description;
    }

    @Override
    public boolean test(JvmInstallationMetadata metadata) {
        final JvmVendor vendor = metadata.getVendor();
        return test(vendor);
    }

    public boolean test(JvmVendor vendor) {
        return matcher.test(vendor);
    }

    @Override
    public boolean matches(String vendor) {
        return test(JvmVendor.fromString(vendor));
    }

    @Override
    public String toString() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJvmVendorSpec that = (DefaultJvmVendorSpec) o;
        return Objects.equal(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(description);
    }
}
