/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.net.URI;

/**
 * This test ensures that we call {@link URI#toASCIIString()} and not {@link URI#toString()}.
 * <p>
 * When a {@code file:} URI containing non-ascii characters (such as accented letters) is converted
 * to a {@link String} using {@code toString()}, the result can contain the non-ascii characters unescaped.
 * When reading that string back to a {@code URI} and then trying to parse such a URL with {@link java.nio.file.Paths#get(URI)}
 * it can result in an {@link java.net.URISyntaxException} being thrown.
 * Using {@code toASCIIString()} results in the canonical representation of the URI that is compatible with any ASCII-based encoding.
 *
 * See <a href="https://www.w3.org/Addressing/URL/3_URI_Choices.html">W3C design choices for URI</a>
 */
@AnalyzeClasses(
    packages = "org.gradle",
    importOptions = ImportOption.DoNotIncludeJars.class
)
public final class UriToStringTest {
    @ArchTest
    public static final ArchRule uri_toString_is_not_used =
        ArchRuleDefinition.noClasses()
            .should().callMethod(URI.class, "toString")
            .because("URI.toString() does not encode non-ascii characters, use URI.toASCIString() instead.")
            .allowEmptyShould(true);
}
