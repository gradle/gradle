/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.platform.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static java.util.Arrays.asList;

public class Architectures {

    public static final KnownArchitecture X86 = new KnownArchitecture("x86", "i386", "ia-32", "i686");
    public static final KnownArchitecture X86_64 = new KnownArchitecture("x86-64", "x86_64", "amd64", "x64");
    public static final KnownArchitecture IA_64 = new KnownArchitecture("ia-64", "ia64");
    public static final KnownArchitecture ARM_V7 = new KnownArchitecture("arm-v7", "armv7", "arm", "arm32");
    public static final KnownArchitecture AARCH64 = new KnownArchitecture("aarch64", "arm-v8", "arm64");

    private static final List<KnownArchitecture> KNOWN_ARCHITECTURES = asList(
            X86,
            X86_64,
            IA_64,
            ARM_V7,
            AARCH64,
            new KnownArchitecture("ppc"),
            new KnownArchitecture("ppc64"),
            new KnownArchitecture("sparc-v8", "sparc", "sparc32"),
            new KnownArchitecture("sparc-v9", "sparc64", "ultrasparc")
    );

    public static ArchitectureInternal forInput(String input) {
        for (KnownArchitecture knownArchitecture : KNOWN_ARCHITECTURES) {
            if (knownArchitecture.isAlias(input.toLowerCase(Locale.ROOT))) {
                return new DefaultArchitecture(knownArchitecture.getCanonicalName());
            }
        }
        return new DefaultArchitecture(input);
    }

    public static ArchitectureInternal of(KnownArchitecture architecture) {
        return new DefaultArchitecture(architecture.canonicalName);
    }

    public static class KnownArchitecture {
        private final String canonicalName;
        private final List<String> aliases;

        public KnownArchitecture(String canonicalName, String... aliases) {
            this.canonicalName = canonicalName;
            this.aliases = Arrays.asList(aliases);
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public boolean isAlias(String input) {
            return canonicalName.equals(input) || aliases.contains(input);
        }
    }
}
