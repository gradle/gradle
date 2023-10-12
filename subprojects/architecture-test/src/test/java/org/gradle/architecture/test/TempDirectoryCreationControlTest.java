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

package org.gradle.architecture.test;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;

import static com.tngtech.archunit.base.DescribedPredicate.doNot;
import static com.tngtech.archunit.core.domain.Formatters.ensureSimpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethod;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "org.gradle")
@ArchIgnore
public class TempDirectoryCreationControlTest {

    private static final String RATIONALE =
        "for security reasons, all temporary file creation should through TemporaryFileProvider";

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_File_createTempFile =
        classes()
            .that(doNot(belongToAnyOf(TestFile.class)))
            .should(not(callMethod(File.class, "createTempFile", String.class, String.class)))
            .because(RATIONALE);

    @ArchTest
    public static final ArchRule forbid_calls_to_guava_Files_createTempDir =
        classes()
            .should(not(callMethod(com.google.common.io.Files.class, "createTempDir")))
            .because(RATIONALE);

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_File_createTempFile_overload =
        classes()
            .that(doNot(belongToAnyOf(org.gradle.api.internal.file.temp.TempFiles.class)))
            .should(not(callMethod(File.class, "createTempFile", String.class, String.class, File.class)))
            .because(String.format(
                "for security reasons, all createTempFile calls taking a 'directory' argument must go through `%s`",
                org.gradle.api.internal.file.temp.TempFiles.class
            ));

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_Files_createTempFile =
        classes()
            .should(not(callMethod(Files.class, "createTempFile", String.class, String.class, FileAttribute[].class)))
            .because(RATIONALE);

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_Files_createTempDirectory =
        classes()
            .should(not(callMethod(Files.class, "createTempDirectory", String.class, FileAttribute[].class)))
            .because(RATIONALE);

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_kotlin_Files_createTempDir =
        classes()
            .should(not(callMethodWithName("kotlin.io.FilesKt", "createTempDir$default")))
            .because(RATIONALE);

    @ArchTest
    public static final ArchRule forbid_illegal_calls_to_kotlin_Files_createTempFile =
        classes()
            .should(not(callMethodWithName("kotlin.io.FilesKt", "createTempFile$default")))
            .because(RATIONALE);


    public static ArchCondition<JavaClass> callMethodWithName(String ownerName, String methodName) {
        return callMethodWhere(JavaCall.Predicates.target(HasOwner.Predicates.With.<JavaClass>owner(name(ownerName)))
            .and(JavaCall.Predicates.target(name(methodName))))
            .as("call method %s.%s", ensureSimpleName(ownerName), methodName);
    }
}
