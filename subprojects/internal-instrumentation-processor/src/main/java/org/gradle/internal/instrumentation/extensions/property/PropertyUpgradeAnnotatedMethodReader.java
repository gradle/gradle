/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.extensions.property;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeRequestExtra.UpgradedPropertyType;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallInterceptionRequestImpl;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableInfoImpl;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.CallableOwnerInfo;
import org.gradle.internal.instrumentation.model.CallableReturnTypeInfo;
import org.gradle.internal.instrumentation.model.ImplementationInfoImpl;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterInfoImpl;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES;
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.GROOVY_PROPERTY_GETTER;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.INSTANCE_METHOD;
import static org.gradle.internal.instrumentation.model.ParameterKindInfo.METHOD_PARAMETER;
import static org.gradle.internal.instrumentation.model.ParameterKindInfo.RECEIVER;
import static org.gradle.internal.instrumentation.processor.AbstractInstrumentationProcessor.PROJECT_NAME_OPTIONS;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

public class PropertyUpgradeAnnotatedMethodReader implements AnnotatedMethodReaderExtension {

    private static final Type DEFAULT_TYPE = Type.getType(UpgradedProperty.DefaultValue.class);

    private final String projectName;

    public PropertyUpgradeAnnotatedMethodReader(ProcessingEnvironment processingEnv) {
        this.projectName = getProjectName(processingEnv);
    }

    private static String getProjectName(ProcessingEnvironment processingEnv) {
        String projectName = processingEnv.getOptions().get(PROJECT_NAME_OPTIONS);
        if (projectName == null || projectName.isEmpty()) {
            return null;
        }
        return Stream.of(projectName.split("-"))
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
            .collect(Collectors.joining());
    }

    private String getGroovyInterceptorsClassName() {
        return GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES + "_" + projectName;
    }

    private String getJavaInterceptorsClassName() {
        return JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES + "_" + projectName;
    }

    @Override
    public Collection<Result> readRequest(ExecutableElement method) {
        Optional<? extends AnnotationMirror> annotation = AnnotationUtils.findAnnotationMirror(method, UpgradedProperty.class);
        if (!annotation.isPresent()) {
            return Collections.emptySet();
        }

        if (projectName == null) {
            // We validate project name here because we want to fail only if there is an @UpgradedProperty annotation used in the project
            return Collections.singletonList(new InvalidRequest("Project name is not specified or is empty. Use -A" + PROJECT_NAME_OPTIONS + "=<projectName> compiler option to set the project name."));
        } else if (!method.getParameters().isEmpty() || !method.getSimpleName().toString().startsWith("get")) {
            return Collections.singletonList(new InvalidRequest(String.format("Method '%s.%s' annotated with @UpgradedProperty should be a simple getter: name should start with 'get' and method should not have any parameters.", method.getEnclosingElement(), method)));
        }

        try {
            String propertyName = getPropertyName(method);
            Type originalType = extractOriginalType(method, annotation.get());
            AnnotationMirror annotationMirror = annotation.get();
            boolean isFluentSetter = AnnotationUtils.findAnnotationValue(annotationMirror, "fluentSetter")
                .map(v -> (Boolean) v.getValue())
                .orElse(false);
            CallInterceptionRequest groovyPropertyRequest = createGroovyPropertyInterceptionRequest(propertyName, method, originalType);
            CallInterceptionRequest jvmGetterRequest = createJvmGetterInterceptionRequest(propertyName, method, originalType);
            CallInterceptionRequest jvmSetterRequest = createJvmSetterInterceptionRequest(propertyName, method, originalType, isFluentSetter);
            return Arrays.asList(new Success(groovyPropertyRequest), new Success(jvmGetterRequest), new Success(jvmSetterRequest));
        } catch (AnnotationReadFailure failure) {
            return Collections.singletonList(new InvalidRequest(failure.reason));
        }
    }

    private static Type extractOriginalType(ExecutableElement method, AnnotationMirror annotation) {
        Optional<? extends AnnotationValue> annotationValue = AnnotationUtils.findAnnotationValue(annotation, "originalType");
        Type type = annotationValue.map(v -> extractType((TypeMirror) v.getValue())).orElse(DEFAULT_TYPE);
        if (!type.equals(DEFAULT_TYPE)) {
            return type;
        }
        return extractOriginalTypeFromGeneric(method, method.getReturnType());
    }

    private static Type extractOriginalTypeFromGeneric(ExecutableElement method, TypeMirror typeMirror) {
        String typeName = method.getReturnType() instanceof DeclaredType
            ? ((DeclaredType) method.getReturnType()).asElement().toString()
            : method.getReturnType().toString();
        if (typeName.equals(RegularFileProperty.class.getName()) || typeName.equals(DirectoryProperty.class.getName())) {
            return Type.getType(File.class);
        } else if (typeName.equals(Property.class.getName()) && ((DeclaredType) typeMirror).getTypeArguments().size() == 1) {
            return extractType(((DeclaredType) typeMirror).getTypeArguments().get(0));
        } else if (typeName.equals(ConfigurableFileCollection.class.getName())) {
            return Type.getType(FileCollection.class);
        } else if (typeName.equals(MapProperty.class.getName())) {
            return Type.getType(Map.class);
        } else if (typeName.equals(ListProperty.class.getName())) {
            return Type.getType(List.class);
        } else if (typeName.equals(SetProperty.class.getName())) {
            return Type.getType(Set.class);
        } else {
            throw new AnnotationReadFailure(String.format("Cannot extract original type for method '%s.%s: %s'. Use explicit @UpgradedProperty#originalType instead.", method.getEnclosingElement(), method, typeMirror));
        }
    }

    private CallInterceptionRequest createGroovyPropertyInterceptionRequest(String propertyName, ExecutableElement method, Type originalType) {
        // TODO: Class name should be read from an annotation
        String interceptorsClassName = getGroovyInterceptorsClassName();
        List<RequestExtra> extras = Arrays.asList(new RequestExtra.OriginatingElement(method), new RequestExtra.InterceptGroovyCalls(interceptorsClassName));
        List<ParameterInfo> parameters = Collections.singletonList(new ParameterInfoImpl("receiver", extractType(method.getEnclosingElement().asType()), RECEIVER));
        return new CallInterceptionRequestImpl(
            extractCallableInfo(GROOVY_PROPERTY_GETTER, method, originalType, propertyName, parameters),
            extractImplementationInfo(method, originalType, "get", Collections.emptyList()),
            extras
        );
    }

    private CallInterceptionRequest createJvmGetterInterceptionRequest(String propertyName, ExecutableElement method, Type originalType) {
        List<RequestExtra> extras = getJvmRequestExtras(propertyName, method, false);
        String capitalize = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        String callableName = originalType.equals(Type.BOOLEAN_TYPE) ? "is" + capitalize : "get" + capitalize;
        return new CallInterceptionRequestImpl(
            extractCallableInfo(INSTANCE_METHOD, method, originalType, callableName, Collections.emptyList()),
            extractImplementationInfo(method, originalType, "get", Collections.emptyList()),
            extras
        );
    }

    private CallInterceptionRequest createJvmSetterInterceptionRequest(String propertyName, ExecutableElement method, Type originalType, boolean isFluentSetter) {
        Type returnType = isFluentSetter ? extractType(method.getEnclosingElement().asType()) : Type.VOID_TYPE;
        String callableName = method.getSimpleName().toString().replaceFirst("get", "set");
        List<ParameterInfo> parameters = Collections.singletonList(new ParameterInfoImpl("arg0", originalType, METHOD_PARAMETER));
        List<RequestExtra> extras = getJvmRequestExtras(propertyName, method, isFluentSetter);
        return new CallInterceptionRequestImpl(
            extractCallableInfo(INSTANCE_METHOD, method, returnType, callableName, parameters),
            extractImplementationInfo(method, returnType, "set", parameters),
            extras
        );
    }

    @Nonnull
    private List<RequestExtra> getJvmRequestExtras(String propertyName, ExecutableElement method, boolean isFluentSetter) {
        String interceptorsClassName = getJavaInterceptorsClassName();
        List<RequestExtra> extras = new ArrayList<>();
        extras.add(new RequestExtra.OriginatingElement(method));
        // TODO: Class name should be read from an annotation
        extras.add(new RequestExtra.InterceptJvmCalls(interceptorsClassName));
        String implementationClass = getGeneratedClassName(method.getEnclosingElement());
        UpgradedPropertyType upgradedPropertyType = UpgradedPropertyType.from(extractType(method.getReturnType()));
        extras.add(new PropertyUpgradeRequestExtra(propertyName, isFluentSetter, implementationClass, method.getSimpleName().toString(), upgradedPropertyType));
        return extras;
    }

    private static CallableInfo extractCallableInfo(CallableKindInfo kindInfo, ExecutableElement methodElement, Type returnType, String callableName, List<ParameterInfo> parameters) {
        CallableOwnerInfo owner = new CallableOwnerInfo(extractType(methodElement.getEnclosingElement().asType()), true);
        CallableReturnTypeInfo returnTypeInfo = new CallableReturnTypeInfo(returnType);
        return new CallableInfoImpl(kindInfo, owner, callableName, returnTypeInfo, parameters);
    }

    private static ImplementationInfoImpl extractImplementationInfo(ExecutableElement method, Type returnType, String methodPrefix, List<ParameterInfo> parameters) {
        Type owner = extractType(method.getEnclosingElement().asType());
        Type implementationOwner = Type.getObjectType(getGeneratedClassName(method.getEnclosingElement()));
        String implementationName = "access_" + methodPrefix + "_" + getPropertyName(method);
        String implementationDescriptor = Type.getMethodDescriptor(returnType, toArray(owner, parameters));
        return new ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor);
    }

    private static String getGeneratedClassName(Element originalType) {
        return "org.gradle.internal.classpath.generated." + originalType.getSimpleName() + "_Adapter";
    }

    private static Type[] toArray(Type owner, List<ParameterInfo> parameters) {
        Type[] array = new Type[1 + parameters.size()];
        array[0] = owner;
        int i = 1;
        for (ParameterInfo parameter : parameters) {
            array[i++] = parameter.getParameterType();
        }
        return array;
    }

    private static String getPropertyName(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        String property = methodName.startsWith("get") ? methodName.substring(3) : methodName;
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }

    // TODO Consolidate with AnnotationCallInterceptionRequestReaderImpl#Failure
    private static class AnnotationReadFailure extends RuntimeException {
        final String reason;

        private AnnotationReadFailure(String reason) {
            this.reason = reason;
        }
    }
}
