/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.*;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static org.gradle.util.CollectionUtils.findFirst;

public class ModelRuleInspector {

    private final static Set<Class<? extends Annotation>> TYPE_ANNOTATIONS = ImmutableSet.of(Model.class, Mutate.class, Finalize.class);

    private static String typeAnnotationsDescription() {
        String desc = Joiner.on(", ").join(Iterables.transform(TYPE_ANNOTATIONS, new Function<Class<? extends Annotation>, String>() {
            public String apply(Class<? extends Annotation> input) {
                return input.getName();
            }
        }));

        return "[" + desc + "]";
    }

    private static <T> T toInstance(Class<T> source) {
        try {
            Constructor<T> declaredConstructor = source.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            return declaredConstructor.newInstance();
        } catch (InstantiationException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getTargetException());
        }
    }

    private static RuntimeException invalid(Class<?> source, String reason) {
        return new InvalidModelRuleDeclarationException("Type " + source.getName() + " is not a valid model rule source: " + reason);
    }

    private static RuntimeException invalid(String description, Method method, String reason) {
        StringBuilder sb = new StringBuilder();
        new MethodModelRuleDescriptor(method).describeTo(sb);
        sb.append(" is not a valid ").append(description).append(": ").append(reason);
        return new InvalidModelRuleDeclarationException(sb.toString());
    }

    public static void rule(final ModelRegistry modelRegistry, final Method method, final boolean isFinalizer, final Factory<?> instance) {
        List<ModelReference<?>> bindings = references(method);

        ModelReference<?> subject = bindings.get(0);
        List<ModelReference<?>> inputs = bindings.subList(1, bindings.size());
        MethodModelMutator<?> mutator = toMutator(method, instance, subject, inputs);

        if (isFinalizer) {
            modelRegistry.finalize(mutator);
        } else {
            modelRegistry.mutate(mutator);
        }
    }

    private static <T> MethodModelMutator<T> toMutator(Method method, Factory<?> instance, ModelReference<T> first, List<ModelReference<?>> tail) {
        return new MethodModelMutator<T>(method, first, tail, instance);
    }

    private static List<ModelReference<?>> references(Method method) {
        Type[] types = method.getGenericParameterTypes();
        ImmutableList.Builder<ModelReference<?>> inputBindingBuilder = ImmutableList.builder();
        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            Annotation[] paramAnnotations = method.getParameterAnnotations()[i];
            inputBindingBuilder.add(reference(paramType, paramAnnotations));
        }
        return inputBindingBuilder.build();
    }

    private static <T> ModelReference<T> reference(Type type, Annotation[] annotations) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        @SuppressWarnings("unchecked") ModelType<T> cast = (ModelType<T>) ModelType.of(type);
        return ModelReference.of(path == null ? null : ModelPath.path(path), cast);
    }

    // TODO return a richer data structure that provides meta data about how the source was found, for use is diagnostics
    public Set<Class<?>> getDeclaredSources(Class<?> container) {
        Class<?>[] declaredClasses = container.getDeclaredClasses();
        if (declaredClasses.length == 0) {
            return Collections.emptySet();
        } else {
            ImmutableSet.Builder<Class<?>> found = ImmutableSet.builder();
            for (Class<?> declaredClass : declaredClasses) {
                if (declaredClass.isAnnotationPresent(RuleSource.class)) {
                    found.add(declaredClass);
                }
            }

            return found.build();
        }
    }

    // TODO should either return the extracted rule, or metadata about the extraction (i.e. for reporting etc.)
    public <T> void inspect(Class<T> source, ModelRegistry modelRegistry) {
        validate(source);
        final Method[] methods = source.getDeclaredMethods();

        // sort for determinism
        Arrays.sort(methods, new Comparator<Method>() {
            public int compare(Method o1, Method o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        for (Method method : methods) {
            Annotation annotation = getTypeAnnotation(method);
            if (annotation != null) {
                if (annotation instanceof Model) {
                    creationMethod(modelRegistry, method, (Model) annotation);
                } else if (annotation instanceof Mutate) {
                    mutationMethod(modelRegistry, method, false);
                } else if (annotation instanceof Finalize) {
                    mutationMethod(modelRegistry, method, true);
                } else {
                    throw new IllegalStateException("Unhandled rule type annotation: " + annotation);
                }
            }
        }
    }

    private void mutationMethod(ModelRegistry modelRegistry, final Method method, boolean finalize) {
        if (method.getTypeParameters().length > 0) {
            throw invalid("model rule", method, "cannot have type variables (i.e. cannot be a generic method)");
        }

        rule(modelRegistry, method, finalize, new Factory<Object>() {
            public Object create() {
                return toInstance(method.getDeclaringClass());
            }
        });
    }

    private Annotation getTypeAnnotation(Method method) {
        Annotation annotation = null;
        for (Annotation declaredAnnotation : method.getDeclaredAnnotations()) {
            if (TYPE_ANNOTATIONS.contains(declaredAnnotation.annotationType())) {
                if (annotation == null) {
                    annotation = declaredAnnotation;
                } else {
                    throw invalid("model rule", method, "can only be annotated with one of " + typeAnnotationsDescription());
                }
            }
        }

        return annotation;
    }

    private void creationMethod(ModelRegistry modelRegistry, Method method, Model modelAnnotation) {
        // TODO validations on method: synthetic, bridge methods, varargs, abstract, native

        // TODO validate model name
        String modelName = determineModelName(modelAnnotation, method);

        if (method.getTypeParameters().length > 0) {
            throw invalid("model creation rule", method, "cannot have type variables (i.e. cannot be a generic method)");
        }

        // TODO validate the return type (generics?)

        ModelType<?> returnType = ModelType.of(method.getGenericReturnType());

        doRegisterCreation(method, returnType, modelName, modelRegistry);
    }

    private <T, R> void doRegisterCreation(final Method method, final ModelType<R> type, final String modelName, final ModelRegistry modelRegistry) {
        ModelPath path = ModelPath.path(modelName);
        List<ModelReference<?>> references = references(method);
        @SuppressWarnings("unchecked") Class<T> clazz = (Class<T>) method.getDeclaringClass();
        @SuppressWarnings("unchecked") Class<R> returnTypeClass = (Class<R>) type.getRawClass();
        JavaMethod<T, R> methodWrapper = JavaReflectionUtil.method(clazz, returnTypeClass, method);

        modelRegistry.create(new MethodModelCreator<R, T>(type, path, references, method, clazz, methodWrapper));
    }

    private static class MethodModelCreator<R, T> implements ModelCreator {
        private final ModelType<R> type;
        private final ModelPath path;
        private final ModelPromise promise;
        private List<ModelReference<?>> inputs;
        private final Method method;
        private final Class<T> clazz;
        private final JavaMethod<T, R> methodWrapper;

        public MethodModelCreator(ModelType<R> type, ModelPath path, List<ModelReference<?>> inputs, Method method, Class<T> clazz, JavaMethod<T, R> methodWrapper) {
            this.type = type;
            this.path = path;
            this.inputs = inputs;
            this.promise = new SingleTypeModelPromise(type);
            this.method = method;
            this.clazz = clazz;
            this.methodWrapper = methodWrapper;
        }

        public ModelPath getPath() {
            return path;
        }

        public ModelPromise getPromise() {
            return promise;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public ModelAdapter create(Inputs inputs) {
            R instance = invoke(inputs);
            if (instance == null) {
                throw new ModelRuleExecutionException(getDescriptor(), "rule returned null");
            }

            return InstanceModelAdapter.of(type, instance);
        }

        private R invoke(Inputs inputs) {
            T instance = Modifier.isStatic(method.getModifiers()) ? null : toInstance(clazz);
            if (inputs.size() == 0) {
                return methodWrapper.invoke(instance);
            } else {
                Object[] args = new Object[inputs.size()];
                for (int i = 0; i < inputs.size(); i++) {
                    args[i] = inputs.get(i, this.inputs.get(i).getType()).getInstance();
                }

                return methodWrapper.invoke(instance, args);
            }
        }

        public ModelRuleDescriptor getDescriptor() {
            return new MethodModelRuleDescriptor(method);
        }
    }

    private String determineModelName(Model modelAnnotation, Method method) {
        String annotationValue = modelAnnotation.value();
        if (annotationValue == null || annotationValue.isEmpty()) {
            return method.getName();
        } else {
            return annotationValue;
        }
    }

    /**
     * Validates that the given class is effectively static and has no instance state.
     *
     * @param source the class the validate
     */
    public void validate(Class<?> source) throws InvalidModelRuleDeclarationException {

        // TODO - exceptions thrown here should point to some extensive documentation on the concept of class rule sources

        int modifiers = source.getModifiers();

        if (Modifier.isInterface(modifiers)) {
            throw invalid(source, "must be a class, not an interface");
        }
        if (Modifier.isAbstract(modifiers)) {
            throw invalid(source, "class cannot be abstract");
        }

        if (source.getEnclosingClass() != null) {
            if (Modifier.isStatic(modifiers)) {
                if (Modifier.isPrivate(modifiers)) {
                    throw invalid(source, "class cannot be private");
                }
            } else {
                throw invalid(source, "enclosed classes must be static and non private");
            }
        }

        Class<?> superclass = source.getSuperclass();
        if (!superclass.equals(Object.class)) {
            throw invalid(source, "cannot have superclass");
        }

        Constructor<?>[] constructors = source.getDeclaredConstructors();
        if (constructors.length > 1) {
            throw invalid(source, "cannot have more than one constructor");
        }

        Constructor<?> constructor = constructors[0];
        if (constructor.getParameterTypes().length > 0) {
            throw invalid(source, "constructor cannot take any arguments");
        }

        Field[] fields = source.getDeclaredFields();
        for (Field field : fields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                throw invalid(source, "field " + field.getName() + " is not static final");
            }
        }

    }

    private static class MethodModelMutator<T> implements org.gradle.model.internal.core.ModelMutator<T> {
        private final MethodModelRuleDescriptor descriptor;
        private final Method bindingMethod;
        private final ModelReference<T> subject;
        private final List<ModelReference<?>> inputs;
        private final Factory<?> instance;

        public MethodModelMutator(Method method, ModelReference<T> subject, List<ModelReference<?>> inputs, Factory<?> instance) {
            this.bindingMethod = method;
            this.subject = subject;
            this.inputs = inputs;
            this.instance = instance;
            this.descriptor = new MethodModelRuleDescriptor(method);
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        public ModelReference<T> getSubject() {
            return subject;
        }

        public List<ModelReference<?>> getInputs() {
            return inputs;
        }

        public void mutate(T object, Inputs inputs) {
            Object[] args = new Object[1 + this.inputs.size()];
            args[0] = object;
            for (int i = 0; i < inputs.size(); ++i) {
                args[i + 1] = inputs.get(i, this.inputs.get(i).getType()).getInstance();
            }

            bindingMethod.setAccessible(true);

            try {
                bindingMethod.invoke(instance.create(), args);
            } catch (Exception e) {
                Throwable t = e;
                if (t instanceof InvocationTargetException) {
                    t = e.getCause();
                }

                throw UncheckedException.throwAsUncheckedException(t);
            }
        }
    }

}
