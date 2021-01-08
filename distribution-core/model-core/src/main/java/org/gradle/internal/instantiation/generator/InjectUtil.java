/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import org.gradle.internal.logging.text.TreeFormatter;

import javax.inject.Inject;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

class InjectUtil {
    /**
     * Selects the single injectable constructor for the given type.
     * The type must either have only one public or package-private default constructor,
     * or it should have a single constructor annotated with {@literal @}{@link Inject}.
     *
     * @param type the type to find the injectable constructor of.
     * @param reportAs errors are reported against this type, useful when the real type is generated, and we'd like to show the original type in messages instead.
     */
    public static <T> ClassGenerator.GeneratedConstructor<T> selectConstructor(ClassGenerator.GeneratedClass<T> type, Class<?> reportAs) {
        List<ClassGenerator.GeneratedConstructor<T>> constructors = type.getConstructors();

        if (constructors.size() == 1) {
            ClassGenerator.GeneratedConstructor<T> constructor = constructors.get(0);
            if (constructor.getParameterTypes().length == 0 && isPublicOrPackageScoped(type.getGeneratedClass(), constructor)) {
                return constructor;
            }
            if (constructor.getAnnotation(Inject.class) != null) {
                return constructor;
            }
            if (constructor.getParameterTypes().length == 0) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("The constructor for type ");
                formatter.appendType(reportAs);
                formatter.append(" should be public or package protected or annotated with @Inject.");
                throw new IllegalArgumentException(formatter.toString());
            } else {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("The constructor for type ");
                formatter.appendType(reportAs);
                formatter.append(" should be annotated with @Inject.");
                throw new IllegalArgumentException(formatter.toString());
            }
        }

        List<ClassGenerator.GeneratedConstructor<T>> injectConstructors = new ArrayList<ClassGenerator.GeneratedConstructor<T>>();
        for (ClassGenerator.GeneratedConstructor<T> constructor : constructors) {
            if (constructor.getAnnotation(Inject.class) != null) {
                injectConstructors.add(constructor);
            }
        }

        if (injectConstructors.isEmpty()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(reportAs);
            formatter.append(" has no constructor that is annotated with @Inject.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (injectConstructors.size() > 1) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(reportAs);
            formatter.append(" has multiple constructors that are annotated with @Inject.");
            throw new IllegalArgumentException(formatter.toString());
        }

        return injectConstructors.get(0);
    }

    private static boolean isPublicOrPackageScoped(Class<?> type, ClassGenerator.GeneratedConstructor<?> constructor) {
        if (isPackagePrivate(type.getModifiers())) {
            return !Modifier.isPrivate(constructor.getModifiers()) && !Modifier.isProtected(constructor.getModifiers());
        } else {
            return Modifier.isPublic(constructor.getModifiers());
        }
    }

    private static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
    }

}
