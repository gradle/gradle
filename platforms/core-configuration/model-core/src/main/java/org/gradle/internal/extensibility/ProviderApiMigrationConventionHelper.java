/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.extensibility;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jspecify.annotations.Nullable;

import java.beans.Introspector;
import java.util.Map;
import java.util.Set;

/**
 * Routes legacy {@code getConventionMapping().map("oldName", ...)} calls to the corresponding
 * renamed lazy property when a property has been migrated from a {@code File} accessor to a
 * {@link org.gradle.api.file.DirectoryProperty} or {@link org.gradle.api.file.RegularFileProperty}
 * with a different name (e.g. {@code outputDir} -> {@code outputDirectory}).
 *
 * <p>The eager getter is kept as a backward-compatible bridge, but ConventionMapping
 * registration uses the old property name and would otherwise never propagate to the lazy
 * property. This helper holds a hardcoded set of known renames so {@link ConventionAwareHelper}
 * can route the convention to the lazy property's {@code .convention()} API directly.</p>
 *
 * <p>Entries should be removed when the eager bridge is removed in a future major release.</p>
 */
class ProviderApiMigrationConventionHelper {

    /**
     * Map from fully-qualified class/interface name to {oldGetter -> newGetter} for properties
     * whose lazy version has been renamed during the provider-api migration. Both old and new
     * getters are kept on the type — old as a backward-compat bridge returning {@code File},
     * new as the canonical lazy property returning a {@link org.gradle.api.provider.SupportsConvention}.
     */
    private static final Map<String, Map<String, String>> RENAMED_GETTERS = ImmutableMap.<String, Map<String, String>>builder()
        .put("org.gradle.api.plugins.quality.CodeQualityExtension", ImmutableMap.of(
            "getReportsDir", "getReportsDirectory"))
        .put("org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor", ImmutableMap.of(
            "getDestination", "getDestinationFile"))
        .put("org.gradle.api.publish.maven.tasks.GenerateMavenPom", ImmutableMap.of(
            "getDestination", "getDestinationFile"))
        .put("org.gradle.api.tasks.bundling.War", ImmutableMap.of(
            "getWebXml", "getWebXmlFile"))
        .put("org.gradle.api.tasks.compile.GroovyCompileOptions", ImmutableMap.of(
            "getConfigurationScript", "getConfigurationScriptFile",
            "getStubDir", "getStubDirectory"))
        .put("org.gradle.api.tasks.javadoc.Groovydoc", ImmutableMap.of(
            "getDestinationDir", "getDestinationDirectory"))
        .put("org.gradle.api.tasks.javadoc.Javadoc", ImmutableMap.of(
            "getDestinationDir", "getDestinationDirectory"))
        .put("org.gradle.api.tasks.scala.ScalaDoc", ImmutableMap.of(
            "getDestinationDir", "getDestinationDirectory"))
        .put("org.gradle.jvm.application.tasks.CreateStartScripts", ImmutableMap.of(
            "getOutputDir", "getOutputDirectory",
            "getUnixScript", "getUnixScriptFile",
            "getWindowsScript", "getWindowsScriptFile"))
        .put("org.gradle.testing.jacoco.plugins.JacocoTaskExtension", ImmutableMap.of(
            "getClassDumpDir", "getClassDumpDirectory"))
        .build();

    /**
     * Pre-computed set of all old getter names across all entries. Used as a fast-path filter
     * to skip the class hierarchy walk when the property name isn't one we know about.
     */
    private static final Set<String> ALL_OLD_GETTERS = computeAllOldGetters();

    private static Set<String> computeAllOldGetters() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Map<String, String> renames : RENAMED_GETTERS.values()) {
            builder.addAll(renames.keySet());
        }
        return builder.build();
    }

    /**
     * If {@code propertyName} on {@code sourceClass} (or any of its supertypes/interfaces) has
     * been renamed, returns the new property name. Otherwise returns {@code null}.
     */
    @Nullable
    static String findRenamedProperty(Class<?> sourceClass, String propertyName) {
        String oldGetter = "get" + capitalize(propertyName);
        if (!ALL_OLD_GETTERS.contains(oldGetter)) {
            return null;
        }
        String newGetter = findRenamedGetter(sourceClass, oldGetter);
        return newGetter == null ? null : Introspector.decapitalize(newGetter.substring(3));
    }

    @Nullable
    private static String findRenamedGetter(@Nullable Class<?> clazz, String oldGetter) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }
        Map<String, String> renames = RENAMED_GETTERS.get(clazz.getName());
        if (renames != null) {
            String replacement = renames.get(oldGetter);
            if (replacement != null) {
                return replacement;
            }
        }
        String result = findRenamedGetter(clazz.getSuperclass(), oldGetter);
        if (result != null) {
            return result;
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            result = findRenamedGetter(iface, oldGetter);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ProviderApiMigrationConventionHelper() {
    }
}
