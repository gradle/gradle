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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link ProviderApiMigrationConventionHelper#findRenamedProperty} resolves
 * every entry in its internal {@code RENAMED_GETTERS} map, plus relevant subclasses,
 * subinterfaces, and implementation classes that walk through the class hierarchy.
 *
 * <p>The renamed classes span multiple platform modules. This test lives in
 * {@code :architecture-test} because that module already pulls in
 * {@code testRuntimeOnly(projects.distributionsFull)}, giving it access to every renamed type
 * on the test classpath; adding the same dependencies to {@code :model-core} would bloat that
 * foundational module's test classpath.
 *
 * <p>Lives in package {@code org.gradle.internal.extensibility} to access the package-private
 * {@link ProviderApiMigrationConventionHelper#findRenamedProperty} method directly.
 */
class ProviderApiMigrationRenamedPropertiesTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("renamedProperties")
    void findRenamedPropertyResolvesRename(String label, String className, String oldProperty, String expectedNewProperty) throws ClassNotFoundException {
        Class<?> sourceClass = Class.forName(className);
        String actual = ProviderApiMigrationConventionHelper.findRenamedProperty(sourceClass, oldProperty);
        assertEquals(expectedNewProperty, actual);
    }

    private static Stream<Arguments> renamedProperties() {
        return Stream.of(
            // Direct entries — one row per key in RENAMED_GETTERS
            entry("CodeQualityExtension.reportsDir",          "org.gradle.api.plugins.quality.CodeQualityExtension",     "reportsDir",           "reportsDirectory"),
            entry("GenerateIvyDescriptor.destination",        "org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor",  "destination",          "destinationFile"),
            entry("GenerateMavenPom.destination",             "org.gradle.api.publish.maven.tasks.GenerateMavenPom",     "destination",          "destinationFile"),
            entry("War.webXml",                               "org.gradle.api.tasks.bundling.War",                       "webXml",               "webXmlFile"),
            entry("GroovyCompileOptions.configurationScript", "org.gradle.api.tasks.compile.GroovyCompileOptions",       "configurationScript",  "configurationScriptFile"),
            entry("GroovyCompileOptions.stubDir",             "org.gradle.api.tasks.compile.GroovyCompileOptions",       "stubDir",              "stubDirectory"),
            entry("Groovydoc.destinationDir",                 "org.gradle.api.tasks.javadoc.Groovydoc",                  "destinationDir",       "destinationDirectory"),
            entry("Javadoc.destinationDir",                   "org.gradle.api.tasks.javadoc.Javadoc",                    "destinationDir",       "destinationDirectory"),
            entry("ScalaDoc.destinationDir",                  "org.gradle.api.tasks.scala.ScalaDoc",                     "destinationDir",       "destinationDirectory"),
            entry("CreateStartScripts.outputDir",             "org.gradle.jvm.application.tasks.CreateStartScripts",     "outputDir",            "outputDirectory"),
            entry("CreateStartScripts.unixScript",            "org.gradle.jvm.application.tasks.CreateStartScripts",     "unixScript",           "unixScriptFile"),
            entry("CreateStartScripts.windowsScript",         "org.gradle.jvm.application.tasks.CreateStartScripts",     "windowsScript",        "windowsScriptFile"),
            entry("JacocoTaskExtension.classDumpDir",         "org.gradle.testing.jacoco.plugins.JacocoTaskExtension",   "classDumpDir",         "classDumpDirectory"),

            // Subclasses of CodeQualityExtension — walks through getSuperclass()
            entry("CheckstyleExtension (subclass)",           "org.gradle.api.plugins.quality.CheckstyleExtension",      "reportsDir",           "reportsDirectory"),
            entry("PmdExtension (subclass)",                  "org.gradle.api.plugins.quality.PmdExtension",             "reportsDir",           "reportsDirectory"),
            entry("CodeNarcExtension (subclass)",             "org.gradle.api.plugins.quality.CodeNarcExtension",        "reportsDir",           "reportsDirectory")
        );
    }

    private static Arguments entry(String label, String className, String oldProperty, String expectedNewProperty) {
        return Arguments.of(label, className, oldProperty, expectedNewProperty);
    }
}
