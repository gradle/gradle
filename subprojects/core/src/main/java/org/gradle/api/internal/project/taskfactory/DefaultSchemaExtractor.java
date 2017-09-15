/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@NonNullApi
public class DefaultSchemaExtractor implements SchemaExtractor {
    // Avoid reflecting on classes we know we don't need to look at
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class
    );

    private final static List<? extends PropertyAnnotationHandler> HANDLERS = Arrays.asList(
        new InputFilePropertyAnnotationHandler(),
        new InputDirectoryPropertyAnnotationHandler(),
        new InputFilesPropertyAnnotationHandler(),
        new OutputFilePropertyAnnotationHandler(),
        new OutputFilesPropertyAnnotationHandler(),
        new OutputDirectoryPropertyAnnotationHandler(),
        new OutputDirectoriesPropertyAnnotationHandler(),
        new InputPropertyAnnotationHandler(),
        new DestroysPropertyAnnotationHandler(),
        new NestedBeanPropertyAnnotationHandler(),
        new NoOpPropertyAnnotationHandler(Inject.class),
        new NoOpPropertyAnnotationHandler(Console.class),
        new NoOpPropertyAnnotationHandler(Internal.class),
        new NoOpPropertyAnnotationHandler(OptionValues.class)
    );

    private final Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationHandlers;
    private final Multimap<Class<? extends Annotation>, Class<? extends Annotation>> annotationOverrides;
    private final Set<Class<? extends Annotation>> relevantAnnotationTypes;

    public DefaultSchemaExtractor(PropertyAnnotationHandler... customAnnotationHandlers) {
        this(Arrays.asList(customAnnotationHandlers));
    }

    public DefaultSchemaExtractor(Iterable<? extends PropertyAnnotationHandler> customAnnotationHandlers) {
        Iterable<PropertyAnnotationHandler> allAnnotationHandlers = Iterables.concat(HANDLERS, customAnnotationHandlers);
        Map<Class<? extends Annotation>, PropertyAnnotationHandler> annotationsHandlers = Maps.uniqueIndex(allAnnotationHandlers, new Function<PropertyAnnotationHandler, Class<? extends Annotation>>() {
            @Override
            public Class<? extends Annotation> apply(PropertyAnnotationHandler handler) {
                return handler.getAnnotationType();
            }
        });
        this.annotationHandlers = annotationsHandlers;
        this.annotationOverrides = collectAnnotationOverrides(allAnnotationHandlers);
        this.relevantAnnotationTypes = collectRelevantAnnotationTypes(annotationsHandlers.keySet());
    }

    private static Multimap<Class<? extends Annotation>, Class<? extends Annotation>> collectAnnotationOverrides(Iterable<PropertyAnnotationHandler> allAnnotationHandlers) {
        ImmutableSetMultimap.Builder<Class<? extends Annotation>, Class<? extends Annotation>> builder = ImmutableSetMultimap.builder();
        for (PropertyAnnotationHandler handler : allAnnotationHandlers) {
            if (handler instanceof OverridingPropertyAnnotationHandler) {
                builder.put(((OverridingPropertyAnnotationHandler) handler).getOverriddenAnnotationType(), handler.getAnnotationType());
            }
        }
        return builder.build();
    }

    private static Set<Class<? extends Annotation>> collectRelevantAnnotationTypes(Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return ImmutableSet.<Class<? extends Annotation>>builder()
            .addAll(propertyTypeAnnotations)
            .add(Optional.class)
            .add(SkipWhenEmpty.class)
            .add(PathSensitive.class)
            .build();
    }

    public <T> ChangeDetectionClassInfo extractClassInfo(Class<T> type) {
        ImmutableSortedSet.Builder<ChangeDetectionProperty> annotatedPropertiesBuilder = ImmutableSortedSet.naturalOrder();
        ImmutableList.Builder<TaskClassValidationMessage> validationMessages = ImmutableList.builder();
        parseProperties(type, annotatedPropertiesBuilder, validationMessages);
        return new ChangeDetectionClassInfo(annotatedPropertiesBuilder.build(), validationMessages.build());
    }

    @Override
    public SchemaRoot extractSchema(Object instance) {
        ChangeDetectionClassInfo classInfo = extractClassInfo(instance.getClass());
        ImmutableMap.Builder<String, SchemaNode> children = ImmutableMap.builder();
        for (ChangeDetectionProperty changeDetectionProperty : classInfo.getAnnotatedProperties()) {
            String propertyName = changeDetectionProperty.getName();
            UpdateAction configureAction = changeDetectionProperty.getConfigureAction();
            if (changeDetectionProperty.isNested()) {
                TaskPropertyValue value = changeDetectionProperty.getValue(instance);
                if (value.getValue() == null) {
                    children.put(propertyName, new SchemaProperty(changeDetectionProperty, instance, configureAction));
                } else {
                    children.put(propertyName, new NestedSchema(extractSchema(value.getValue()).getChildren(), value, configureAction));
                }
            } else {
                children.put(propertyName, new SchemaProperty(changeDetectionProperty, instance, configureAction));
            }
        }
        return new SchemaRoot(children.build(), instance);
    }

    private <T> void parseProperties(Class<T> type, ImmutableSet.Builder<ChangeDetectionProperty> annotatedProperties, final ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages) {
        final Set<Class<? extends Annotation>> propertyTypeAnnotations = annotationHandlers.keySet();
        final Map<String, DefaultTaskPropertyActionContext> propertyContexts = Maps.newLinkedHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                List<Getter> getters = getGetters(type);
                for (Getter getter : getters) {
                    Method method = getter.getMethod();
                    String fieldName = getter.getName();
                    Field field = fields.get(fieldName);

                    DefaultTaskPropertyActionContext propertyContext = propertyContexts.get(fieldName);
                    if (propertyContext == null) {
                        propertyContext = new DefaultTaskPropertyActionContext(propertyTypeAnnotations, fieldName, method, false, validationMessages);
                        propertyContexts.put(fieldName, propertyContext);
                    }

                    if (field != null) {
                        propertyContext.setInstanceVariableField(field);
                    }
                    Iterable<Annotation> declaredAnnotations = mergeDeclaredAnnotations(propertyContext, method, field);

                    // Discard overridden property type annotations when an overriding annotation is also present
                    Iterable<Annotation> overriddenAnnotations = filterOverridingAnnotations(declaredAnnotations, propertyTypeAnnotations);

                    recordAnnotations(propertyContext, overriddenAnnotations, propertyTypeAnnotations);
                }
            }
        });
        for (DefaultTaskPropertyActionContext propertyContext : propertyContexts.values()) {
            ChangeDetectionProperty property = createProperty(propertyContext);
            if (property != null) {
                annotatedProperties.add(property);
            }
        }
    }

    public static class ChangeDetectionClassInfo {
        private final ImmutableSortedSet<ChangeDetectionProperty> annotatedProperties;
        private final ImmutableList<TaskClassValidationMessage> validationMessages;

        public ChangeDetectionClassInfo(Set<ChangeDetectionProperty> annotatedProperties, List<TaskClassValidationMessage> validationMessages) {
            this.annotatedProperties = ImmutableSortedSet.copyOf(annotatedProperties);
            this.validationMessages = ImmutableList.copyOf(validationMessages);
        }

        public ImmutableSortedSet<ChangeDetectionProperty> getAnnotatedProperties() {
            return annotatedProperties;
        }

        public ImmutableList<TaskClassValidationMessage> getValidationMessages() {
            return validationMessages;
        }
    }

    private Iterable<Annotation> mergeDeclaredAnnotations(TaskPropertyActionContext propertyContext, Method method, Field field) {
        Collection<Annotation> methodAnnotations = collectRelevantAnnotations(method.getDeclaredAnnotations());
        if (field == null) {
            return methodAnnotations;
        }
        Collection<Annotation> fieldAnnotations = collectRelevantAnnotations(field.getDeclaredAnnotations());
        if (fieldAnnotations.isEmpty()) {
            return methodAnnotations;
        }
        if (methodAnnotations.isEmpty()) {
            return fieldAnnotations;
        }

        for (Annotation methodAnnotation : methodAnnotations) {
            Iterator<Annotation> iFieldAnnotation = fieldAnnotations.iterator();
            while (iFieldAnnotation.hasNext()) {
                Annotation fieldAnnotation = iFieldAnnotation.next();
                if (methodAnnotation.annotationType().equals(fieldAnnotation.annotationType())) {
                    propertyContext.validationMessage("has both a getter and field declared with annotation @" + methodAnnotation.annotationType().getSimpleName());
                    iFieldAnnotation.remove();
                }
            }
        }

        return Iterables.concat(methodAnnotations, fieldAnnotations);
    }

    private Collection<Annotation> collectRelevantAnnotations(Annotation[] annotations) {
        List<Annotation> relevantAnnotations = Lists.newArrayListWithCapacity(annotations.length);
        for (Annotation annotation : annotations) {
            if (relevantAnnotationTypes.contains(annotation.annotationType())) {
                relevantAnnotations.add(annotation);
            }
        }
        return relevantAnnotations;
    }

    private Iterable<Annotation> filterOverridingAnnotations(final Iterable<Annotation> declaredAnnotations, final Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        return Iterables.filter(declaredAnnotations, new Predicate<Annotation>() {
            @Override
            public boolean apply(Annotation input) {
                Class<? extends Annotation> annotationType = input.annotationType();
                if (!propertyTypeAnnotations.contains(annotationType)) {
                    return true;
                }
                for (Class<? extends Annotation> overridingAnnotation : annotationOverrides.get(annotationType)) {
                    for (Annotation declaredAnnotation : declaredAnnotations) {
                        if (declaredAnnotation.annotationType().equals(overridingAnnotation)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
    }

    private void recordAnnotations(TaskPropertyActionContext propertyContext, Iterable<Annotation> annotations, Set<Class<? extends Annotation>> propertyTypeAnnotations) {
        Set<Class<? extends Annotation>> declaredPropertyTypes = Sets.newLinkedHashSet();
        for (Annotation annotation : annotations) {
            if (propertyTypeAnnotations.contains(annotation.annotationType())) {
                declaredPropertyTypes.add(annotation.annotationType());
            }
            propertyContext.addAnnotation(annotation);
        }

        if (declaredPropertyTypes.size() > 1) {
            propertyContext.validationMessage("has conflicting property types declared: "
                    + Joiner.on(", ").join(Iterables.transform(declaredPropertyTypes, new Function<Class<? extends Annotation>, String>() {
                    @Override
                    public String apply(Class<? extends Annotation> annotationType) {
                        return "@" + annotationType.getSimpleName();
                    }
                }))
            );
        }
    }

    @Nullable
    private ChangeDetectionProperty createProperty(DefaultTaskPropertyActionContext propertyContext) {
        Class<? extends Annotation> propertyType = propertyContext.getPropertyType();
        if (propertyType != null) {
            if (propertyContext.isAnnotationPresent(Optional.class)) {
                propertyContext.setOptional(true);
            }

            PropertyAnnotationHandler handler = annotationHandlers.get(propertyType);
            handler.attachActions(propertyContext);

            return propertyContext.createProperty();
        } else {
            propertyContext.validationMessage("is not annotated with an input or output annotation");
            return null;
        }
    }

    private static Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = Maps.newHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private static List<Getter> getGetters(Class<?> type) {
        Method[] methods = type.getDeclaredMethods();
        List<Getter> getters = Lists.newArrayListWithCapacity(methods.length);
        for (Method method : methods) {
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            // We only care about getters
            if (accessorType == null || accessorType == PropertyAccessorType.SETTER) {
                continue;
            }
            // We only care about actual methods the user added
            if (method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                continue;
            }
            getters.add(new DefaultSchemaExtractor.Getter(method, accessorType.propertyNameFor(method)));
        }
        Collections.sort(getters);
        return getters;
    }

    private static class Getter implements Comparable<Getter> {
        private final Method method;
        private final String name;

        public Getter(Method method, String name) {
            this.method = method;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Method getMethod() {
            return method;
        }

        @Override
        public int compareTo(Getter o) {
            // Sort "is"-getters before "get"-getters when both are available
            return method.getName().compareTo(o.method.getName());
        }
    }

    private static class DefaultTaskPropertyActionContext implements TaskPropertyActionContext {
        private final Set<Class<? extends Annotation>> propertyTypeAnnotations;
        private final String name;
        private final Method method;
        private final List<Annotation> annotations = Lists.newArrayList();
        private final boolean cacheable;
        private final ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages;
        private Field instanceVariableField;
        private ValidationAction validationAction;
        private UpdateAction configureAction;
        private boolean optional;
        private Class<?> nestedType;
        private Class<? extends Annotation> propertyType;

        public DefaultTaskPropertyActionContext(Set<Class<? extends Annotation>> propertyTypeAnnotations, String name, Method method, boolean cacheable, ImmutableCollection.Builder<TaskClassValidationMessage> validationMessages) {
            this.propertyTypeAnnotations = propertyTypeAnnotations;
            this.name = name;
            this.method = method;
            this.cacheable = cacheable;
            this.validationMessages = validationMessages;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        @Override
        public Class<?> getValueType() {
            return instanceVariableField != null
                ? instanceVariableField.getType()
                : method.getReturnType();
        }

        @Override
        public void addAnnotation(Annotation annotation) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            // Record the most specific property type annotation only
            if (propertyType == null && isPropertyTypeAnnotation(annotationType)) {
                propertyType = annotationType;
            }
            // Record the most specific annotation only
            if (!isAnnotationPresent(annotation.annotationType())) {
                annotations.add(annotation);
            }
        }

        private boolean isPropertyTypeAnnotation(Class<? extends Annotation> annotationType) {
            return propertyTypeAnnotations.contains(annotationType);
        }

        @Override
        @Nullable
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            for (Annotation annotation : annotations) {
                if (annotationType.equals(annotation.annotationType())) {
                    return annotationType.cast(annotation);
                }
            }
            return null;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            return getAnnotation(annotationType) != null;
        }

        @Override
        public void setInstanceVariableField(@Nullable Field instanceVariableField) {
            if (this.instanceVariableField == null && instanceVariableField != null) {
                this.instanceVariableField = instanceVariableField;
            }
        }

        @Override
        public boolean isOptional() {
            return optional;
        }

        @Override
        public boolean isCacheable() {
            return cacheable;
        }

        @Override
        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        @Override
        public void setValidationAction(ValidationAction action) {
            this.validationAction = action;
        }

        @Override
        public void setConfigureAction(UpdateAction action) {
            this.configureAction = action;
        }

        @Nullable
        public Class<?> getNestedType() {
            return nestedType;
        }

        @Override
        public void setNestedType(Class<?> nestedType) {
            this.nestedType = nestedType;
        }

        @Nullable
        public ChangeDetectionProperty createProperty() {
            if (configureAction == null && validationAction == null) {
                return null;
            }
            return new ChangeDetectionProperty(name, propertyType, method, validationAction, configureAction, optional);
        }

        @Override
        public void validationMessage(String message) {
            validationMessages.add(TaskClassValidationMessage.property(name, message));
        }
    }


    public static class ChangeDetectionProperty implements Comparable<ChangeDetectionProperty> {
        private static final UpdateAction NO_OP_CONFIGURATION_ACTION = new UpdateAction() {};

        private static final ValidationAction NO_OP_VALIDATION_ACTION = new ValidationAction() {
            public void validate(String propertyName, Object value, Collection<String> messages) {
            }
        };

        private final String propertyName;
        private final Class<? extends Annotation> propertyType;
        private final Method method;
        private final ValidationAction validationAction;
        private final UpdateAction configureAction;
        private final boolean optional;

        ChangeDetectionProperty(String propertyName, Class<? extends Annotation> propertyType, Method method, @Nullable ValidationAction validationAction, @Nullable UpdateAction configureAction, boolean optional) {
            this.propertyName = propertyName;
            this.propertyType = propertyType;
            this.method = method;
            this.validationAction = validationAction == null ? NO_OP_VALIDATION_ACTION : validationAction;
            this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
            this.optional = optional;
        }

        @Override
        public String toString() {
            return String.format("@%s %s", propertyType.getSimpleName(), propertyName);
        }

        public String getName() {
            return propertyName;
        }

        public Class<? extends Annotation> getPropertyType() {
            return propertyType;
        }

        public boolean isNested() {
            return propertyType == Nested.class;
        }

        public UpdateAction getConfigureAction() {
            return configureAction;
        }

        public TaskPropertyValue getValue(final Object bean) {

            final Object value = DeprecationLogger.whileDisabled(new Factory<Object>() {
                public Object create() {
                    return JavaReflectionUtil.method(Object.class, method).invoke(bean);
                }
            });

            return new TaskPropertyValue() {
                @Override
                public Object getValue() {
                    return value;
                }

                @Override
                @SuppressWarnings("ConstantConditions")
                public void checkNotNull(Collection<String> messages) {
                    if (value == null && !optional) {
                        messages.add(String.format("No value has been specified for property '%s'.", propertyName));
                    }
                }

                @Override
                @SuppressWarnings("ConstantConditions")
                public void checkValid(Collection<String> messages) {
                    if (value != null) {
                        validationAction.validate(propertyName, value, messages);
                    }
                }
            };
        }

        @Override
        public int compareTo(ChangeDetectionProperty o) {
            return propertyName.compareTo(o.getName());
        }
    }

    public interface SchemaNode extends Callable<Object> {
        UpdateAction getConfigureAction();
    }

    private static final UpdateAction NO_OP_CONFIGURATION_ACTION = new UpdateAction() {};

    public static class SchemaProperty implements SchemaNode {

        private final ChangeDetectionProperty property;
        private final Object parentValue;
        private final Supplier<TaskPropertyValue> valueSupplier = Suppliers.memoize(new Supplier<TaskPropertyValue>() {
            @Override
            public TaskPropertyValue get() {
                return property.getValue(parentValue);
            }
        });
        private final UpdateAction configureAction;

        SchemaProperty(ChangeDetectionProperty property, Object parentValue, @Nullable UpdateAction configureAction) {
            this.property = property;
            this.parentValue = parentValue;
            this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
        }

        @Override
        public UpdateAction getConfigureAction() {
            return configureAction;
        }

        @Override
        public Object call() throws Exception {
            return valueSupplier.get().getValue();
        }
    }

    public static class SchemaRoot implements SchemaNode {
        private final Map<String, SchemaNode> children;
        private final Object instance;

        SchemaRoot(Map<String, SchemaNode> children, Object instance) {
            this.children = children;
            this.instance = instance;
        }

        public Map<String, SchemaNode> getChildren() {
            return children;
        }

        @Override
        public UpdateAction getConfigureAction() {
            return NO_OP_CONFIGURATION_ACTION;
        }

        @Override
        public Object call() throws Exception {
            return instance;
        }
    }

    public static class NestedSchema implements SchemaNode {
        private final Map<String, SchemaNode> children;
        private final TaskPropertyValue value;
        private final UpdateAction configureAction;

        NestedSchema(Map<String, SchemaNode> children, TaskPropertyValue value, @Nullable UpdateAction configureAction) {
            this.children = children;
            this.value = value;
            this.configureAction = configureAction == null ? NO_OP_CONFIGURATION_ACTION : configureAction;
        }

        public Map<String, SchemaNode> getChildren() {
            return children;
        }

        @Override
        public UpdateAction getConfigureAction() {
            return configureAction;
        }

        @Override
        public Object call() throws Exception {
            return value.getValue();
        }
    }
}
