/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.util;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.GeneratedClosure;
import org.gradle.api.Action;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.internal.Actions;
import org.gradle.internal.metaobject.ConfigureDelegate;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.util.internal.ClosureBackedAction;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

import static org.gradle.util.internal.CollectionUtils.toStringList;

/**
 * Contains utility methods to configure objects with Groovy Closures.
 * <p>
 * Plugins should avoid using this class and methods that use {@link groovy.lang.Closure} as this makes the plugin harder to use in other languages. Instead, plugins should create methods that use {@link Action}.
 * Here's an example pseudocode:
 * <pre class='autoTested'>
 *     interface MyOptions {
 *         RegularFileProperty getOptionsFile()
 *     }
 *     abstract class MyExtension {
 *         private final MyOptions options
 *
 *         {@literal @}Inject abstract ObjectFactory getObjectFactory()
 *
 *         public MyExtension() {
 *             this.options = getObjectFactory().newInstance(MyOptions)
 *         }
 *
 *         public void options(Action{@literal <?} extends MyOptions{@literal >}  action) {
 *              action.execute(options)
 *         }
 *     }
 *     extensions.create("myExtension", MyExtension)
 *     myExtension {
 *         options {
 *             optionsFile = layout.projectDirectory.file("options.properties")
 *         }
 *     }
 * </pre>
 * <p>
 * Gradle automatically generates a Closure-taking method at runtime for each method with an {@link Action} as a single argument as long as the object is created with {@link org.gradle.api.model.ObjectFactory#newInstance(Class, Object...)}.
 * <p>
 * As a last resort, to apply some configuration represented by a Groovy Closure, a plugin can use {@link org.gradle.api.Project#configure(Object, Closure)}.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class ConfigureUtil {

    public static <T> T configureByMap(Map<?, ?> properties, T delegate) {
        logDeprecation(8);
        return configureByMapInternal(properties, delegate);
    }

    private static <T> T configureByMapInternal(Map<?, ?> properties, T delegate) {
        if (properties.isEmpty()) {
            return delegate;
        }
        DynamicObject dynamicObject = DynamicObjectUtil.asDynamicObject(delegate);

        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = entry.getKey().toString();
            Object value = entry.getValue();

            DynamicInvokeResult result = dynamicObject.trySetProperty(name, value);
            if (result.isFound()) {
                continue;
            }

            result = dynamicObject.tryInvokeMethod(name, value);
            if (!result.isFound()) {
                throw dynamicObject.setMissingProperty(name);
            }
        }

        return delegate;
    }

    public static <T> T configureByMap(Map<?, ?> properties, T delegate, Collection<?> mandatoryKeys) {
        if (!mandatoryKeys.isEmpty()) {
            Collection<String> missingKeys = toStringList(mandatoryKeys);
            missingKeys.removeAll(toStringList(properties.keySet()));
            if (!missingKeys.isEmpty()) {
                throw new IncompleteInputException("Input configuration map does not contain following mandatory keys: " + missingKeys, missingKeys);
            }
        }
        logDeprecation(7);
        return configureByMapInternal(properties, delegate);
    }

    /**
     * Incomplete input exception.
     */
    @Deprecated
    public static class IncompleteInputException extends RuntimeException {
        private final Collection missingKeys;

        public IncompleteInputException(String message, Collection missingKeys) {
            super(message);
            this.missingKeys = missingKeys;
            logDeprecation(7);
        }

        public Collection getMissingKeys() {
            return missingKeys;
        }
    }

    /**
     * <p>Configures {@code target} with {@code configureClosure}, via the {@link Configurable} interface if necessary.</p>
     *
     * <p>If {@code target} does not implement {@link Configurable} interface, it is set as the delegate of a clone of
     * {@code configureClosure} with a resolve strategy of {@code DELEGATE_FIRST}.</p>
     *
     * <p>If {@code target} does implement the {@link Configurable} interface, the {@code configureClosure} will be passed to
     * {@code delegate}'s {@link Configurable#configure(Closure)} method.</p>
     *
     * @param configureClosure The configuration closure
     * @param target The object to be configured
     * @return The delegate param
     */
    public static <T> T configure(@Nullable Closure configureClosure, T target) {
        // TODO log deprecation once the shadow plugin is fixed
        if (configureClosure == null) {
            return target;
        }

        if (target instanceof Configurable) {
            ((Configurable) target).configure(configureClosure);
        } else {
            configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target));
        }

        return target;
    }

    /**
     * Creates an action that uses the given closure to configure objects of type T.
     */
    public static <T> Action<T> configureUsing(@Nullable final Closure configureClosure) {
        logDeprecation(7);
        if (configureClosure == null) {
            return Actions.doNothing();
        }

        return new WrappedConfigureAction<T>(configureClosure);
    }

    /**
     * Called from an object's {@link Configurable#configure} method.
     */
    public static <T> T configureSelf(@Nullable Closure configureClosure, T target) {
        logDeprecation(7);
        if (configureClosure == null) {
            return target;
        }

        configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target));
        return target;
    }

    /**
     * Called from an object's {@link Configurable#configure} method.
     */
    public static <T> T configureSelf(@Nullable Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        logDeprecation(7);
        if (configureClosure == null) {
            return target;
        }

        configureTarget(configureClosure, target, closureDelegate);
        return target;
    }

    private static <T> void configureTarget(Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        if (!(configureClosure instanceof GeneratedClosure)) {
            new ClosureBackedAction<T>(configureClosure, Closure.DELEGATE_FIRST, false).execute(target);
            return;
        }

        // Hackery to make closure execution faster, by short-circuiting the expensive property and method lookup on Closure
        Closure withNewOwner = configureClosure.rehydrate(target, closureDelegate, configureClosure.getThisObject());
        new ClosureBackedAction<T>(withNewOwner, Closure.OWNER_ONLY, false).execute(target);
    }

    private static void logDeprecation(int majorVersion) {
        DeprecationLogger.deprecateType(ConfigureUtil.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(majorVersion, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    /**
     * Wrapper configure action.
     *
     * @param <T> the action type.
     */
    @Deprecated
    public static class WrappedConfigureAction<T> implements Action<T> {
        private final Closure configureClosure;

        WrappedConfigureAction(Closure configureClosure) {
            this.configureClosure = configureClosure;
        }

        @Override
        public void execute(T t) {
            configure(configureClosure, t);
        }

        public Closure getConfigureClosure() {
            return configureClosure;
        }
    }
}
