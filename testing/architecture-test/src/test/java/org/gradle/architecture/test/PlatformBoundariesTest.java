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
import com.google.gson.reflect.TypeToken;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.getClassFile;

/**
 * Keep lower level platforms from depending on higher level platform projects.
 *
 * This test consumes the definition of platform directories and relationships from
 * the outer build definition via system properties.
 *
 * This test assumes that the classes are in the build directory of each project.
 */
@AnalyzeClasses(packages = "org.gradle")
public class PlatformBoundariesTest {

    static class PlatformData {
        final String name;
        final List<String> dirs;
        final List<String> uses;

        PlatformData(String name, List<String> dirs, List<String> uses) {
            this.name = name;
            this.dirs = dirs;
            this.uses = uses;
        }
    }

    private static final List<Path> allPlatformDirs;
    private static final Map<String, List<Path>> dirsByPlatform;
    private static final Map<String, List<Path>> usedDirsByPlatform;
    private static final Map<String, List<Path>> allowedDirsByPlatform;

    static {
        Path basePath = Paths.get(System.getProperty("org.gradle.architecture.platforms-base-path"));
        Path jsonPath = Paths.get(System.getProperty("org.gradle.architecture.platforms-json"));

        List<PlatformData> platforms;
        try (Reader reader = new FileReader(jsonPath.toFile())) {
            platforms = new Gson().fromJson(reader, new TypeToken<List<PlatformData>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        allPlatformDirs = platforms.stream().flatMap(p -> resolvePlatformDirs(basePath, p.dirs)).collect(toList());

        dirsByPlatform = collectDirsByPlatform(platforms, p -> resolvePlatformDirs(basePath, p.dirs));

        usedDirsByPlatform = collectDirsByPlatform(platforms, p -> p.uses.stream().flatMap(
            use -> dirsByPlatform.get(use).stream()
        ));

        allowedDirsByPlatform = collectDirsByPlatform(platforms, p -> Stream.concat(
            usedDirsByPlatform.getOrDefault(p.name, allPlatformDirs).stream(),
            dirsByPlatform.get(p.name).stream()
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
                String ownPlatformName = platformNameOf(ownClassFile, ownJavaClass.getName());
                javaClassesAccessedFrom(ownJavaClass).map(targetJavaClass -> {
                    Path targetClassFile = getClassFile(targetJavaClass);
                    if (targetClassFile != null && isInAnyPlatformDirectory(targetClassFile)) {
                        return Pair.ofNonNull(targetJavaClass.getName(), targetClassFile);
                    }
                    return null;
                }).filter(Objects::nonNull).forEach(pair -> {
                    String targetClassName = pair.getLeft();
                    Path targetClassFile = pair.getRight();
                    boolean conditionSatisfied = isInPlatformAllowedDirectory(ownPlatformName, targetClassFile);
                    String message;
                    if (conditionSatisfied) {
                        message = String.format(
                            "Class '%s' from platform '%s' does not import any class from forbidden platforms",
                            ownJavaClass.getName(),
                            ownPlatformName
                        );
                    } else {
                        message = String.format(
                            "Class '%s' from platform '%s' imports class '%s' from a forbidden platform '%s'",
                            ownJavaClass.getName(),
                            ownPlatformName,
                            targetClassName,
                            platformNameOf(targetClassFile, targetClassName)
                        );
                    }
                    events.add(new SimpleConditionEvent(
                        ownJavaClass,
                        conditionSatisfied,
                        message
                    ));
                });
            }
        };
    }

    private static Stream<JavaClass> javaClassesAccessedFrom(JavaClass ownJavaClass) {
        return ownJavaClass.getAccessesFromSelf().stream().map(JavaAccess::getTargetOwner).filter(distinctBy(JavaClass::getName));
    }

    private static Map<String, List<Path>> collectDirsByPlatform(List<PlatformData> platforms, Function<PlatformData, Stream<Path>> valueFunction) {
        return platforms.stream().collect(toMap(
            p -> p.name,
            p -> valueFunction.apply(p).distinct().collect(toList())
        ));
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

    private static String platformNameOf(Path classFile, String className) {
        return dirsByPlatform.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(classFile::startsWith)).map(Map.Entry::getKey).findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not find platform for class " + className));
    }

    private static <T> Predicate<T> distinctBy(Function<? super T, ?> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }
}
