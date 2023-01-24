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

import java.util.regex.Pattern;

public interface JvmVendor {

    enum KnownJvmVendor {
        ADOPTIUM("adoptium", "temurin|adoptium|eclipse foundation", "Eclipse Temurin"),
        ADOPTOPENJDK("adoptopenjdk", "AdoptOpenJDK"),
        AMAZON("amazon", "Amazon Corretto"),
        APPLE("apple", "Apple"),
        AZUL("azul systems", "Azul Zulu"),
        BELLSOFT("bellsoft", "BellSoft Liberica"),
        GRAAL_VM("graalvm community", "GraalVM Community"),
        HEWLETT_PACKARD("hewlett-packard", "HP-UX"),
        IBM("ibm", "ibm|international business machines corporation", "IBM"),
        MICROSOFT("microsoft", "Microsoft"),
        ORACLE("oracle", "Oracle"),
        SAP("sap se", "SAP SapMachine"),
        UNKNOWN("gradle", "Unknown Vendor");

        private final String indicatorString;
        private final Pattern indicatorPattern;
        private final String displayName;

        KnownJvmVendor(String indicatorString, String displayName) {
            this.indicatorString = indicatorString;
            this.indicatorPattern = Pattern.compile(indicatorString, Pattern.CASE_INSENSITIVE);
            this.displayName = displayName;
        }

        KnownJvmVendor(String indicatorString, String pattern, String displayName) {
            this.indicatorString = indicatorString;
            this.indicatorPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            this.displayName = displayName;
        }

        private String getDisplayName() {
            return displayName;
        }

        static KnownJvmVendor parse(String rawVendor) {
            if (rawVendor == null) {
                return UNKNOWN;
            }
            for (KnownJvmVendor jvmVendor : KnownJvmVendor.values()) {
                if (jvmVendor.indicatorString.equals(rawVendor)) {
                    return jvmVendor;
                }
                if (jvmVendor.indicatorPattern.matcher(rawVendor).find()) {
                    return jvmVendor;
                }
            }
            return UNKNOWN;
        }

        public JvmVendor asJvmVendor() {
            return JvmVendor.fromString(indicatorString);
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
