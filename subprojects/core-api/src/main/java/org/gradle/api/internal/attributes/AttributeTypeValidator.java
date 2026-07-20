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

package org.gradle.api.internal.attributes;

import org.gradle.api.Named;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Runtime validation helpers for attribute value types.
 * <p>
 * The public {@link org.gradle.api.attributes.Attribute#of(String, Class)} factory validates
 * attribute value types up front at declaration. This helper provides the same allowlist check
 * so it can be reused by attribute rule chains, which warn when a rule is written against an
 * unsupported attribute value type — a case that Java generics don't catch when rules are
 * registered through raw types, wildcards, or reflection.
 * <p>
 * Unlike the factory, this check runs lazily: it fires the first time a rule is exercised during
 * attribute matching (not at rule registration), so a rule whose attribute is never matched emits
 * no warning. When an unsupported type is detected it emits a deprecation warning rather than
 * failing — see {@link #validateRuleTypeParameter}.
 */
public final class AttributeTypeValidator {
    /**
     * Fully-qualified name of a plain enum used as an attribute value type by the Kotlin Gradle
     * Plugin 2.0.x line (empirically observed in 2.0.0 through 2.0.21). KGP 2.1.0+ no longer
     * uses this enum. Not referenced by class literal because the enum lives in the KGP
     * distribution and is not on Gradle's compile classpath.
     */
    private static final String KGP_NATIVE_BUNDLE_ENUM_FQN =
        "org.jetbrains.kotlin.gradle.targets.native.toolchain.KotlinNativeBundleArtifactFormat$KotlinNativeBundleArtifactsTypes";

    /**
     * Returns whether the given class is one of the supported attribute value types:
     * {@code String}, {@code Boolean}, any subtype of {@code Number}, or a type implementing
     * {@link Named}.
     */
    public static boolean isSupportedAttributeType(Class<?> type) {
        return type == String.class
            || type == Boolean.class
            || Number.class.isAssignableFrom(type)
            || Named.class.isAssignableFrom(type);
    }

    /**
     * Returns whether the given type is the specific plain-enum class
     * {@code KotlinNativeBundleArtifactsTypes} used as an attribute value type by the Kotlin
     * Gradle Plugin 2.0.x line (empirically observed in 2.0.0 through 2.0.21). KGP 2.1.0+ no
     * longer uses this enum. Callers may accept this type with a targeted deprecation warning
     * identifying KGP as the source, instead of the generic unsupported-type deprecation.
     * <p>
     * This special case should be removed when compatibility with KGP 2.0.x is no longer required.
     */
    public static boolean isKGPSpecialCase(Class<?> type) {
        return KGP_NATIVE_BUNDLE_ENUM_FQN.equals(type.getName());
    }

    /**
     * Returns whether the given type has, anywhere in its supertype chain (interfaces or
     * superclasses, recursively), an interface whose fully-qualified name is
     * {@code "org.gradle.api.Named"} but whose {@link Class} identity differs from {@link Named}.
     * This "alien Named" situation almost always indicates a shaded or duplicated {@code gradle-api}
     * on the classpath — the same interface loaded twice by different classloaders is treated by
     * the JVM as two unrelated types, so {@code Named.class.isAssignableFrom(type)} returns
     * {@code false} even though the type does implement (a copy of) {@code Named}.
     * <p>
     * This detection is intended to run only after {@link #isSupportedAttributeType(Class)} has
     * already returned {@code false}, so a type that implements our real {@code Named} alongside
     * an alien {@code Named} still short-circuits via that check and never reaches here.
     */
    public static boolean isAlienNamedSpecialCase(Class<?> type) {
        if ("org.gradle.api.Named".equals(type.getName()) && type != Named.class) {
            return true;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (isAlienNamedSpecialCase(iface)) {
                return true;
            }
        }
        Class<?> sup = type.getSuperclass();
        return sup != null && isAlienNamedSpecialCase(sup);
    }

    /**
     * Validates that the type argument the given rule class supplies to the given rule interface
     * (typically {@code AttributeCompatibilityRule} or {@code AttributeDisambiguationRule}) is
     * a supported attribute value type.
     * <p>
     * This performs reflective type-argument extraction on every call, so callers that invoke it
     * on a hot path (e.g. per attribute match) are responsible for throttling it — see the
     * per-instance guard in the rule-chain {@code ValidatingAction}s. It deliberately keeps no
     * static state of its own: caching validated rule classes in a JVM-lifetime static would
     * both leak plugin classloaders across builds and suppress the deprecation on every build
     * after the first in a reused daemon.
     * <p>
     * Silently accepts if:
     * <ul>
     *   <li>the type argument cannot be resolved to a concrete class (rule is itself generic,
     *       uses a wildcard, or hides its type argument behind an abstract intermediate whose
     *       interface uses an unbound variable), or</li>
     *   <li>the type argument is {@link Object} — this is the erasure default for raw-typed rule
     *       declarations, and rejecting it would break test-only "accept-anything" rules that
     *       intentionally don't care about the value type.</li>
     * </ul>
     * <p>
     * Emits a deprecation warning if the type argument is resolved to a class that is not
     * one of the supported attribute value types. This will become an error in Gradle 10.
     */
    public static void validateRuleTypeParameter(Class<?> ruleClass, Class<?> ruleInterface) {
        Class<?> typeArg = extractInterfaceTypeArgument(ruleClass, ruleInterface);
        if (typeArg == null || typeArg == Object.class) {
            return;
        }
        if (!isSupportedAttributeType(typeArg)) {
            DeprecationLogger.deprecate("Using type '" + typeArg.getName() + "' as the type parameter of attribute rule '" + ruleClass.getName() + "'")
                .withContext("Attribute values must be of type String, Boolean, a subtype of Number, or implement " + Named.class.getName() + ". Using an unsupported type may cause failures during dependency resolution, publishing, or configuration cache serialization.")
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "unsupported_attribute_value_type")
                .nagUser();
        }
    }

    /**
     * Walks the given concrete class's superclass/superinterface chain looking for the first
     * declaration of {@code targetInterface} whose type argument resolves to a concrete class.
     * Returns {@code null} if no such declaration is found or the type argument is a wildcard
     * or unbound type variable.
     */
    @Nullable
    private static Class<?> extractInterfaceTypeArgument(Class<?> concrete, Class<?> targetInterface) {
        Class<?> current = concrete;
        while (current != null && current != Object.class) {
            for (Type iface : current.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) iface;
                    if (pt.getRawType() == targetInterface) {
                        Type[] args = pt.getActualTypeArguments();
                        if (args.length == 1 && args[0] instanceof Class) {
                            return (Class<?>) args[0];
                        }
                        return null;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
