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

package org.gradle.integtests.fixtures;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.joining;

/**
 * Enforces a strict contract on Gradle-mode test-gating annotations
 * (annotation types meta-annotated with {@link GradleModeTestingIntent}):
 *
 * <ul>
 *   <li>At most one intent annotation of a given {@link GradleModeTesting mode}
 *       may be reachable on the class hierarchy of a concrete spec.</li>
 *   <li>For any feature method, the set of intent annotations of a given mode
 *       reachable for that feature (class-level intents in the hierarchy plus
 *       the feature method's own intents) must be at most one.</li>
 * </ul>
 *
 * Validation results for a concrete spec class are memoized via {@link ClassValue},
 * so the hierarchy walk runs once per spec class. Per-method checks run on every
 * feature visit and are intentionally not cached: a temporary inconsistency on one
 * method should not poison other, correctly annotated methods of the same spec.
 *
 * <p>Note: an abstract base spec that is never instantiated by a concrete subclass
 * is never validated. Concrete subclasses trigger validation on first feature visit,
 * and inherited conflicts will surface there.
 */
@NullMarked
final class GradleModeTestingIntentValidator {

    private GradleModeTestingIntentValidator() {}

    /**
     * Validates that at most one intent annotation per mode is reachable through
     * the spec's class hierarchy.
     */
    static void validateSpec(Class<?> specClass) {
        ClassLevelVerdict verdict = PER_CLASS.get(specClass);
        if (verdict.error() != null) {
            throw new IllegalStateException(verdict.error());
        }
    }

    /**
     * Validates that at most one intent annotation per mode is reachable for the given
     * feature method — counting both class-level intents in its spec hierarchy and
     * method-level intents on the method itself.
     */
    static void validateFeature(Class<?> specClass, Method featureMethod) {
        validateSpec(specClass);
        ClassLevelVerdict verdict = PER_CLASS.get(specClass);
        Map<GradleModeTesting, IntentSite> methodLevel = collectMethodLevelIntents(specClass, featureMethod);
        for (var entry : methodLevel.entrySet()) {
            IntentSite classSite = verdict.intentsByMode().get(entry.getKey());
            if (classSite != null) {
                throw new IllegalStateException(
                    formatClassVsMethodConflict(specClass, entry.getKey(), classSite, entry.getValue())
                );
            }
        }
    }

    // --- Per-class cache ---

    private static final ClassValue<ClassLevelVerdict> PER_CLASS = new ClassValue<>() {
        @Override
        protected ClassLevelVerdict computeValue(Class<?> specClass) {
            return computeClassLevel(specClass);
        }
    };

    private record ClassLevelVerdict(@Nullable String error, Map<GradleModeTesting, IntentSite> intentsByMode) {}

    /**
     * Where an intent annotation was discovered. Either a class declaration or a method declaration.
     */
    private sealed interface IntentSite {
        Annotation annotation();

        default String annotationName() {
            return "@" + annotation().annotationType().getSimpleName();
        }

        String describe();
    }

    private record ClassSite(Annotation annotation, Class<?> declaringClass) implements IntentSite {
        @Override
        public String describe() {
            return annotationName() + " on class " + declaringClass.getName();
        }
    }

    private record MethodSite(Annotation annotation, Method declaringMethod) implements IntentSite {
        @Override
        public String describe() {
            return annotationName() + " on method "
                + declaringMethod.getDeclaringClass().getName() + "." + declaringMethod.getName() + "()";
        }
    }

    // --- Computation ---

    private static ClassLevelVerdict computeClassLevel(Class<?> specClass) {
        var collected = new EnumMap<GradleModeTesting, List<IntentSite>>(GradleModeTesting.class);
        for (Class<?> c = specClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Annotation ann : c.getDeclaredAnnotations()) {
                GradleModeTestingIntent meta = intentMeta(ann);
                if (meta != null) {
                    collected.computeIfAbsent(meta.mode(), k -> new ArrayList<>()).add(new ClassSite(ann, c));
                }
            }
        }
        return resolveOnePerMode(collected, (mode, sites) -> formatHierarchyConflict(specClass, mode, sites));
    }

    private static Map<GradleModeTesting, IntentSite> collectMethodLevelIntents(Class<?> specClass, Method featureMethod) {
        var collected = new EnumMap<GradleModeTesting, List<IntentSite>>(GradleModeTesting.class);
        for (Annotation ann : featureMethod.getDeclaredAnnotations()) {
            GradleModeTestingIntent meta = intentMeta(ann);
            if (meta != null) {
                collected.computeIfAbsent(meta.mode(), k -> new ArrayList<>()).add(new MethodSite(ann, featureMethod));
            }
        }
        ClassLevelVerdict verdict = resolveOnePerMode(collected,
            (mode, sites) -> formatMethodConflict(specClass, featureMethod, mode, sites));
        if (verdict.error() != null) {
            throw new IllegalStateException(verdict.error());
        }
        return verdict.intentsByMode();
    }

    /**
     * Collapses {@code collected} (mode → list of sites) into a {@link ClassLevelVerdict}
     * where each mode maps to at most one site. If any mode has more than one site,
     * returns a verdict carrying an error built by {@code conflictMessage}.
     */
    private static ClassLevelVerdict resolveOnePerMode(
        Map<GradleModeTesting, List<IntentSite>> collected,
        BiFunction<GradleModeTesting, List<IntentSite>, String> conflictMessage
    ) {
        var resolved = new EnumMap<GradleModeTesting, IntentSite>(GradleModeTesting.class);
        for (var entry : collected.entrySet()) {
            List<IntentSite> sites = entry.getValue();
            if (sites.size() > 1) {
                return new ClassLevelVerdict(conflictMessage.apply(entry.getKey(), sites), Map.of());
            }
            resolved.put(entry.getKey(), sites.get(0));
        }
        return new ClassLevelVerdict(null, resolved);
    }

    private static @Nullable GradleModeTestingIntent intentMeta(Annotation ann) {
        return ann.annotationType().getAnnotation(GradleModeTestingIntent.class);
    }

    // --- Error messages ---

    private static String formatHierarchyConflict(Class<?> specClass, GradleModeTesting mode, List<IntentSite> sites) {
        StringBuilder sb = new StringBuilder()
            .append("Conflicting ").append(mode.displayName())
            .append(" gating on ").append(specClass.getName())
            .append(": multiple class-level intent annotations are reachable for this mode, but at most one is allowed.\n");
        for (IntentSite site : sites) {
            sb.append("  - ").append(site.describe()).append('\n');
        }
        return sb.toString();
    }

    private static String formatClassVsMethodConflict(Class<?> specClass, GradleModeTesting mode, IntentSite classSite, IntentSite methodSite) {
        return "Conflicting " + mode.displayName() + " gating on " + specClass.getName() + ": "
            + classSite.describe() + " conflicts with " + methodSite.describe()
            + ". At most one intent annotation of a given mode may be reachable for a feature.";
    }

    private static String formatMethodConflict(Class<?> specClass, Method featureMethod, GradleModeTesting mode, List<IntentSite> sites) {
        String joined = sites.stream()
            .map(IntentSite::annotationName)
            .collect(joining(" and "));
        return "Conflicting " + mode.displayName() + " gating on " + specClass.getName()
            + ": method " + featureMethod.getDeclaringClass().getName() + "." + featureMethod.getName()
            + "() carries multiple intent annotations for this mode: " + joined
            + ". At most one is allowed.";
    }
}
