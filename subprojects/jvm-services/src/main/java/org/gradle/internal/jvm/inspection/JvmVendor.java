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

package org.gradle.internal.jvm.inspection;

public interface JvmVendor {

    enum KnownJvmVendor {
        ADOPTOPENJDK("adoptopenjdk", "AdoptOpenJDK"),
        AMAZON("amazon", "Amazon Corretto"),
        APPLE("apple", "Apple"),
        AZUL("azul systems", "Zulu"),
        BELLSOFT("bellsoft", "BellSoft Liberica"),
        GRAAL_VM("graalvm community", "GraalVM Community"),
        HEWLETT_PACKARD("hewlett-packard", "HP-UX"),
        IBM("ibm", "IBM"),
        MICROSOFT("microsoft", "Microsoft"),
        ORACLE("oracle", "Oracle"),
        SAP("sap se", "SAP SapMachine"),
        UNKNOWN("gradle", "Unknown Vendor");

        private final String indicatorString;
        private final String displayName;

        KnownJvmVendor(String indicatorString, String displayName) {
            this.indicatorString = indicatorString;
            this.displayName = displayName;
        }

        private String getDisplayName() {
            return displayName;
        }

        static KnownJvmVendor parse(String rawVendor) {
            if (rawVendor == null) {
                return UNKNOWN;
            }
            rawVendor = rawVendor.toLowerCase();
            for (KnownJvmVendor jvmVendor : KnownJvmVendor.values()) {
                if (rawVendor.contains(jvmVendor.indicatorString)) {
                    return jvmVendor;
                }
            }
            return UNKNOWN;
        }
    }

    String getRawVendor();

    KnownJvmVendor getKnownVendor();

    String getDisplayName();

    static JvmVendor fromString(String vendor) {
        return new JvmVendor() {

            @Override
            public String getRawVendor() {
                return vendor;
            }

            @Override
            public KnownJvmVendor getKnownVendor() {
                return KnownJvmVendor.parse(vendor);
            }

            @Override
            public String getDisplayName() {
                final KnownJvmVendor knownVendor = getKnownVendor();
                if(knownVendor != KnownJvmVendor.UNKNOWN) {
                    return knownVendor.getDisplayName();
                }
                return getRawVendor();
            }
        };
    }

}
