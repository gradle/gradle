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

package org.gradle.internal.extensibility

import org.gradle.process.BaseExecSpec
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.process.JavaForkOptions
import org.gradle.process.ProcessForkOptions
import org.gradle.process.internal.DefaultExecSpec
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.DefaultProcessForkOptions
import spock.lang.Specification

/**
 * Algorithm-level tests for {@link ProviderApiMigrationConventionHelper#findRenamedProperty}.
 *
 * <p>Covered hierarchy (the only renamed class reachable from this module's test classpath):
 * <ul>
 *     <li>{@link ProcessForkOptions} — the renamed interface itself.</li>
 *     <li>{@link JavaForkOptions}, {@link BaseExecSpec} — direct subinterfaces.</li>
 *     <li>{@link ExecSpec} — extends {@code BaseExecSpec}.</li>
 *     <li>{@link JavaExecSpec} — diamond inheritance ({@code JavaForkOptions} + {@code BaseExecSpec}).</li>
 *     <li>{@link DefaultProcessForkOptions} — direct implementation.</li>
 *     <li>{@link DefaultExecSpec}, {@link DefaultJavaForkOptions} — subclasses of {@code DefaultProcessForkOptions}.</li>
 * </ul>
 *
 * <p>The comprehensive coverage of every entry in {@code RENAMED_GETTERS} lives in
 * {@code ProviderApiMigrationRenamedPropertiesTest} in {@code :architecture-test}, where the
 * full distribution is on the test runtime classpath.
 */
class ProviderApiMigrationConventionHelperTest extends Specification {

    def "findRenamedProperty resolves workingDir to workingDirectory on #sourceClass.simpleName"() {
        expect:
        ProviderApiMigrationConventionHelper.findRenamedProperty(sourceClass, "workingDir") == "workingDirectory"

        where:
        sourceClass               | _
        ProcessForkOptions        | _   // the renamed interface itself
        JavaForkOptions           | _   // direct subinterface
        BaseExecSpec              | _   // direct subinterface
        ExecSpec                  | _   // ExecSpec -> BaseExecSpec -> ProcessForkOptions
        JavaExecSpec              | _   // diamond: JavaForkOptions + BaseExecSpec -> ProcessForkOptions
        DefaultProcessForkOptions | _   // direct implementation
        DefaultExecSpec           | _   // subclass of DefaultProcessForkOptions
        DefaultJavaForkOptions    | _   // subclass of DefaultProcessForkOptions
    }

    def "findRenamedProperty returns null when #scenario"() {
        expect:
        ProviderApiMigrationConventionHelper.findRenamedProperty(sourceClass, propertyName) == null

        where:
        scenario                                                          | sourceClass        | propertyName
        "the property name is not a known renamed property"               | ProcessForkOptions | "unknownProperty"
        "the property name is a known rename but on an unrelated class"   | String             | "workingDir"
        "the source class is Object"                                      | Object             | "workingDir"
        "the class hierarchy contains no renamed property"                | ArrayList          | "workingDir"
    }
}
