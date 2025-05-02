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
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.toList;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.safeReflect;

@SuppressWarnings("unchecked")
@AnalyzeClasses(packages = "org.gradle")
public class PlatformBoundariesTest {

    private static final List<Path> allPlatformDirs;
    private static final Map<String, List<Path>> dirsByPlatform;
    private static final Map<String, List<Path>> usedDirsByPlatform;

    static {
        Path basePath = Paths.get(System.getProperty("org.gradle.architecture.platforms-base-path"));
        Path jsonPath = Paths.get(System.getProperty("org.gradle.architecture.platforms-json"));
        try (Reader reader = new FileReader(jsonPath.toFile())) {
            Map<String, Map<String, List<String>>> data = (Map<String, Map<String, List<String>>>) new Gson().fromJson(reader, Map.class);
            allPlatformDirs = data.values().stream().flatMap(info -> info.get("dirs").stream().map(basePath::resolve)).collect(toList());
            dirsByPlatform = data.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                v -> v.getValue().get("dirs").stream().map(basePath::resolve).collect(toList())
            ));
            usedDirsByPlatform = data.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                v -> v.getValue().get("uses").stream().flatMap(x -> dirsByPlatform.get(x).stream()).distinct().collect(toList())
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @ArchTest
    public static final ArchRule platform_coupling = freeze(classes()
        .that(areInPlatformDirectories())
        .should(importClassesOnlyFromAllowedPlatforms()));

    private static ArchCondition<? super JavaClass> importClassesOnlyFromAllowedPlatforms() {
        return new ArchCondition<JavaClass>("import classes only from allowed platforms") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Path ownClassFile = getClassFile(item);
                if (ownClassFile == null) {
                    return;
                }
                String ownPlatformName = dirsByPlatform.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(ownClassFile::startsWith)).map(Map.Entry::getKey).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find platform for class " + item.getName()));
                List<Path> allowedDirs = usedDirsByPlatform.getOrDefault(ownPlatformName, PlatformBoundariesTest.allPlatformDirs);
                allowedDirs.addAll(dirsByPlatform.get(ownPlatformName));
                for (JavaAccess<?> access : item.getAccessesFromSelf()) {
                    JavaClass targetOwner = access.getTargetOwner();
                    Path targetClassFile = getClassFile(targetOwner);
                    if (targetClassFile != null && allPlatformDirs.stream().anyMatch(targetClassFile::startsWith)) {
                        boolean foundViolation = allowedDirs.stream().noneMatch(targetClassFile::startsWith);
                        String targetPlatformName = dirsByPlatform.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(targetClassFile::startsWith)).map(Map.Entry::getKey).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Could not find platform for class " + item.getName()));
                        String message;
                        if (foundViolation) {
                            message = String.format("Class '%s' from platform '%s' imports class '%s' from a forbidden platform '%s'", item.getName(), ownPlatformName, targetOwner.getName(), targetPlatformName);
                        } else {
                            message = String.format("Class '%s' from platform '%s' does not import any class from forbidden platforms", item.getName(), ownPlatformName);
                        }
                        events.add(new SimpleConditionEvent(
                            item,
                            !foundViolation,
                            message
                        ));
                    }
                }
            }
        };
    }

    private static DescribedPredicate<? super JavaClass> areInPlatformDirectories() {
        return new DescribedPredicate<JavaClass>("are in platform directories") {
            @Override
            public boolean test(JavaClass javaClass) {
                Path classFile = getClassFile(javaClass);
                if (classFile == null) {
                    return false;
                }
                return allPlatformDirs.stream().anyMatch(classFile::startsWith);
            }
        };
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
