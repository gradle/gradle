/*
 * Copyright 2025 the original author or authors.
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

import com.google.gson.Gson;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.jspecify.annotations.Nullable;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.safeReflect;

/**
 * Keep lower level platforms from depending on higher level platform projects.
 *
 * This test consumes the definition of platform directories and relationships from
 * the outer build definition via system properties.
 *
 * This test assumes that the classes are in the build directory of each project.
 */
@SuppressWarnings("unchecked")
@AnalyzeClasses(packages = "org.gradle")
public class PlatformBoundariesTest {

    private static final List<Path> allPlatformDirs;
    private static final Map<String, List<Path>> dirsByPlatform;
    private static final Map<String, List<Path>> usedDirsByPlatform;
    private static final Map<String, List<Path>> allowedDirsByPlatform;

    static {
        Path basePath = Paths.get(System.getProperty("org.gradle.architecture.platforms-base-path"));
        Path jsonPath = Paths.get(System.getProperty("org.gradle.architecture.platforms-json"));
        Map<String, Map<String, List<String>>> data;
        try (Reader reader = new FileReader(jsonPath.toFile())) {
            data = (Map<String, Map<String, List<String>>>) new Gson().fromJson(reader, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        allPlatformDirs = data.values().stream().flatMap(
            info -> resolvePlatformDirs(basePath, info.get("dirs"))
        ).collect(toList());
        dirsByPlatform = data.entrySet().stream().collect(toMap(
            Map.Entry::getKey,
            v -> resolvePlatformDirs(basePath, v.getValue().get("dirs")).collect(toList())
        ));
        usedDirsByPlatform = data.entrySet().stream().collect(toMap(
            Map.Entry::getKey,
            v -> v.getValue().get("uses").stream().flatMap(
                use -> dirsByPlatform.get(use).stream()
            ).distinct().collect(toList())
        ));
        allowedDirsByPlatform = data.entrySet().stream().collect(toMap(
            Map.Entry::getKey,
            v -> Stream.concat(
                usedDirsByPlatform.getOrDefault(v.getKey(), allPlatformDirs).stream(),
                dirsByPlatform.get(v.getKey()).stream()
            ).distinct().collect(toList())
        ));
    }

    @ArchTest
    public static final ArchRule platform_coupling = freeze(classes()
        .that(areInPlatformDirectories())
        .should(importClassesOnlyFromAllowedPlatforms()));

    private static DescribedPredicate<? super JavaClass> areInPlatformDirectories() {
        return new DescribedPredicate<JavaClass>("are in platform directories") {
            @Override
            public boolean test(JavaClass javaClass) {
                Path classFile = getClassFile(javaClass);
                if (classFile == null) {
                    return false;
                }
                return isInAnyPlatformDirectory(classFile);
            }
        };
    }

    private static ArchCondition<? super JavaClass> importClassesOnlyFromAllowedPlatforms() {
        return new ArchCondition<JavaClass>("import classes only from allowed platforms") {
            @Override
            public void check(JavaClass ownJavaClass, ConditionEvents events) {
                Path ownClassFile = getClassFile(ownJavaClass);
                if (ownClassFile == null) {
                    return;
                }
                String ownPlatformName = platformNameOf(ownJavaClass, ownClassFile);
                for (JavaAccess<?> access : ownJavaClass.getAccessesFromSelf()) {
                    JavaClass targetJavaClass = access.getTargetOwner();
                    Path targetClassFile = getClassFile(targetJavaClass);
                    if (targetClassFile != null && isInAnyPlatformDirectory(targetClassFile)) {
                        boolean conditionSatisfied = isInPlatformAllowedDirectory(ownPlatformName, targetClassFile);
                        String targetPlatformName = platformNameOf(targetJavaClass, targetClassFile);
                        String message;
                        if (conditionSatisfied) {
                            message = String.format("Class '%s' from platform '%s' does not import any class from forbidden platforms", ownJavaClass.getName(), ownPlatformName);
                        } else {
                            message = String.format("Class '%s' from platform '%s' imports class '%s' from a forbidden platform '%s'", ownJavaClass.getName(), ownPlatformName, targetJavaClass.getName(), targetPlatformName);
                        }
                        events.add(new SimpleConditionEvent(
                            ownJavaClass,
                            conditionSatisfied,
                            message
                        ));
                    }
                }
            }
        };
    }

    private static boolean isInAnyPlatformDirectory(Path classFile) {
        return allPlatformDirs.stream().anyMatch(classFile::startsWith);
    }

    private static boolean isInPlatformAllowedDirectory(String platformName, Path classFile) {
        return allowedDirsByPlatform.get(platformName).stream().anyMatch(classFile::startsWith);
    }

    private static Stream<Path> resolvePlatformDirs(Path basePath, List<String> dirNames) {
        return dirNames.stream().map(basePath::resolve);
    }

    private static String platformNameOf(JavaClass javaClass, Path classFile) {
        return dirsByPlatform.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(classFile::startsWith)).map(Map.Entry::getKey).findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not find platform for class " + javaClass.getName()));
    }

    @Nullable
    private static Path getClassFile(JavaClass javaClass) {
        Class<?> reflectedClass = safeReflect(javaClass);
        if (reflectedClass == null) {
            return null;
        }
        CodeSource codeSource = reflectedClass.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        return Paths.get(codeSource.getLocation().getPath());
    }
}
