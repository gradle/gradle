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
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion;

/**
 * Represents a Java Language version
 *
 * @since 6.7
 */
@Incubating
public interface JavaLanguageVersion extends Comparable<JavaLanguageVersion> {

    static JavaLanguageVersion of(int version) {
        JavaLanguageVersion langVersion = DefaultJavaLanguageVersion.KNOWN_VERSIONS.get(version);
        if (langVersion == null) {
            langVersion = new DefaultJavaLanguageVersion(version);
        }
        return langVersion;
    }

    static JavaLanguageVersion of(String version) {
        return of(Integer.parseInt(version));
    }

    /**
     * Return this version as a number, 14 for Java 14.
     *
     * @return the version number
     */
    int asInt();

    /**
     * Return this version as a String, "14" for Java 14.
     *
     * @return the version number
     */
    String asString();

    /**
     * Indicates if this version can interoperate with the other version.
     * <p>
     * For example, Java 14 interoperates with Java 11, but not the opposite.
     *
     * @param other the language version to interoperate with
     *
     * @return {@code true} if this version can interoperate with the other version, {@code false} otherwise
     */
    boolean interoperatesWith(JavaLanguageVersion other);

    /**
     * Indicates if this version can interoperate with the other version.
     * <p>
     * For example, Java 14 interoperates with Java 11, but not the opposite.
     *
     * @param otherVersion the language version to interoperate with, as an {@code int}
     *
     * @return {@code true} if this version can interoperate with the other version, {@code false} otherwise
     */
    boolean interoperatesWith(int otherVersion);

}
