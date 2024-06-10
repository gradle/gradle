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

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacedDeprecation.RemovedIn;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.DefaultValue;
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
import org.gradle.internal.instrumentation.processor.codegen.GradleLazyType;
import org.gradle.internal.instrumentation.processor.extensibility.AnnotatedMethodReaderExtension;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.InvalidRequest;
import org.gradle.internal.instrumentation.processor.modelreader.api.CallInterceptionRequestReader.Result.Success;
import org.gradle.internal.instrumentation.processor.modelreader.impl.AnnotationUtils;
import org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.GROOVY_INTERCEPTORS_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES;
import static org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAME_FOR_PROPERTY_UPGRADES;
import static org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType.BYTECODE_UPGRADE;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.GROOVY_PROPERTY_GETTER;
import static org.gradle.internal.instrumentation.model.CallableKindInfo.INSTANCE_METHOD;
import static org.gradle.internal.instrumentation.model.ParameterKindInfo.METHOD_PARAMETER;
import static org.gradle.internal.instrumentation.model.ParameterKindInfo.RECEIVER;
import static org.gradle.internal.instrumentation.processor.AbstractInstrumentationProcessor.PROJECT_NAME_OPTIONS;
import static org.gradle.internal.instrumentation.processor.codegen.GradleLazyType.FILE_COLLECTION;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractMethodDescriptor;
import static org.gradle.internal.instrumentation.processor.modelreader.impl.TypeUtils.extractType;

public class PropertyUpgradeAnnotatedMethodReader implements AnnotatedMethodReaderExtension {

    private static final Type DEFAULT_TYPE = Type.getType(DefaultValue.class);

    private final String projectName;
    private final Elements elements;
    private final Types types;

    public PropertyUpgradeAnnotatedMethodReader(ProcessingEnvironment processingEnv) {
        this.projectName = getProjectName(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    private static String getProjectName(ProcessingEnvironment processingEnv) {
        String projectName = processingEnv.getOptions().get(PROJECT_NAME_OPTIONS);
        if (projectName == null || projectName.isEmpty()) {
            return null;
        }
        return Stream.of(projectName.split("-"))
            .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1))
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
        Optional<? extends AnnotationMirror> annotation = AnnotationUtils.findAnnotationMirror(method, ReplacesEagerProperty.class);
        if (!annotation.isPresent()) {
            return Collections.emptySet();
        }

        if (projectName == null) {
            // We validate project name here because we want to fail only if there is an @ReplacesEagerProperty annotation used in the project
            return Collections.singletonList(new InvalidRequest("Project name is not specified or is empty. Use -A" + PROJECT_NAME_OPTIONS + "=<projectName> compiler option to set the project name."));
        } else if (!method.getParameters().isEmpty() || !method.getSimpleName().toString().startsWith("get")) {
            return Collections.singletonList(new InvalidRequest(String.format("Method '%s.%s' annotated with @ReplacesEagerProperty should be a simple getter: name should start with 'get' and method should not have any parameters.", method.getEnclosingElement(), method)));
        }

        try {
            AnnotationMirror annotationMirror = annotation.get();
            List<AccessorSpec> accessorSpecs = readAccessorSpecsFromReplacesEagerProperty(method, annotationMirror);
            List<CallInterceptionRequest> requests = new ArrayList<>();
            for (AccessorSpec accessorSpec : accessorSpecs) {
                switch (accessorSpec.accessorType) {
                    case GETTER:
                        if (isGroovyProperty(accessorSpec.methodName)) {
                            CallInterceptionRequest groovyPropertyRequest = createGroovyPropertyInterceptionRequest(accessorSpec, method);
                            requests.add(groovyPropertyRequest);
                        }
                        CallInterceptionRequest jvmGetterRequest = createJvmGetterInterceptionRequest(accessorSpec, method);
                        requests.add(jvmGetterRequest);
                        break;
                    case SETTER:
                        CallInterceptionRequest jvmSetterRequest = createJvmSetterInterceptionRequest(accessorSpec, method);
                        requests.add(jvmSetterRequest);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported accessor type: " + accessorSpec.accessorType);
                }
            }
            return requests.stream()
                .map(Success::new)
                .collect(Collectors.toList());
        } catch (AnnotationReadFailure failure) {
            return Collections.singletonList(new InvalidRequest(failure.reason));
        }
    }

    @SuppressWarnings("unchecked")
    private List<AccessorSpec> readAccessorSpecsFromReplacesEagerProperty(ExecutableElement method, AnnotationMirror annotationMirror) {
        Element element = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotationMirror, "adapter")
            .map(v -> types.asElement((TypeMirror) v.getValue()))
            .orElseThrow(() -> new IllegalArgumentException("Missing adapter value"));
        if (!element.getSimpleName().toString().equals(DefaultValue.class.getSimpleName())) {
            return readAccessorSpecsFromAdapter(element, annotationMirror);
        }

        List<AnnotationMirror> replacedAccessors = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotationMirror, "replacedAccessors")
            .map(v -> (List<AnnotationMirror>) v.getValue())
            .orElseThrow(() -> new AnnotationReadFailure(String.format("Missing 'replacedAccessors' attribute in @%s", ReplacesEagerProperty.class.getSimpleName())));
        if (!replacedAccessors.isEmpty()) {
            DeprecationSpec parentDeprecationSpec = readDeprecationSpec(annotationMirror);
            BinaryCompatibility parentBinaryCompatibility = readBinaryCompatibility(annotationMirror);
            return replacedAccessors.stream()
                .map(annotation -> getAccessorSpec(method, annotation, parentDeprecationSpec, parentBinaryCompatibility))
                .collect(Collectors.toList());
        }
        return Arrays.asList(
            getAccessorSpec(method, AccessorType.GETTER, annotationMirror),
            getAccessorSpec(method, AccessorType.SETTER, annotationMirror)
        );
    }

    private List<AccessorSpec> readAccessorSpecsFromAdapter(Element element, AnnotationMirror annotationMirror) {
        List<ExecutableElement> bridgedMethods = TypeUtils.getExecutableElementsFromElements(Stream.of(element)).stream()
            .filter(method -> method.getAnnotation(BytecodeUpgrade.class) != null)
            .collect(Collectors.toList());
        validateBridgedMethods(element, bridgedMethods);

        return bridgedMethods.stream()
            .map(method -> bridgedMethodToAccessorSpec(method, annotationMirror))
            .collect(Collectors.toList());
    }

    private static void validateBridgedMethods(Element element, List<ExecutableElement> methods) {
        List<String> errors = new ArrayList<>();
        if (!isPackagePrivate(element)) {
            errors.add(String.format("Adapter class '%s' should be package private, but it's not.", element));
        }

        for (ExecutableElement method : methods) {
            if (!method.getModifiers().contains(Modifier.STATIC)) {
                errors.add(String.format("Adapter method '%s.%s' should be static but it's not.", element, method));
            }
            if (!isPackagePrivate(method)) {
                errors.add(String.format("Adapter method '%s.%s' should be package-private but it's not.", element, method));
            }
        }

        if (!errors.isEmpty()) {
            throw new AnnotationReadFailure(String.join("\n", errors));
        }
    }

    private static boolean isPackagePrivate(Element element) {
        return !element.getModifiers().contains(Modifier.PUBLIC)
            && !element.getModifiers().contains(Modifier.PROTECTED)
            && !element.getModifiers().contains(Modifier.PRIVATE);
    }

    private AccessorSpec bridgedMethodToAccessorSpec(ExecutableElement method, AnnotationMirror annotationMirror) {
        DeprecationSpec deprecationSpec = readDeprecationSpec(annotationMirror);
        BinaryCompatibility binaryCompatibility = readBinaryCompatibility(annotationMirror);
        String methodName = method.getSimpleName().toString();
        String propertyName = getPropertyName(methodName);
        AccessorType accessorType = method.getParameters().size() > 1
            ? AccessorType.SETTER
            : AccessorType.GETTER;
        Type returnType = extractType(method.getReturnType());
        Element innerClass = method.getEnclosingElement();
        Element topClass = innerClass.getEnclosingElement();
        PackageElement packageElement = elements.getPackageOf(innerClass);

        // Using $$, since internal classes types has $ and due to
        // that we have some problems translating from asm Type to javapoet TypeName
        String generatedClassName = String.format("%s.$$BridgeFor$$%s$$%s",
            packageElement.getQualifiedName().toString(),
            topClass.getSimpleName().toString(),
            innerClass.getSimpleName().toString()
        );

        List<ParameterInfo> parameters = method.getParameters().stream()
            .skip(1)
            .map(parameter -> new ParameterInfoImpl(
                parameter.getSimpleName().toString(),
                TypeUtils.extractType(parameter.asType()),
                METHOD_PARAMETER
            ))
            .collect(Collectors.toList());
        return new AccessorSpec(
            generatedClassName,
            accessorType,
            propertyName,
            methodName,
            returnType,
            parameters,
            deprecationSpec,
            binaryCompatibility,
            method);
    }

    private DeprecationSpec readDeprecationSpec(AnnotationMirror annotation) {
        AnnotationMirror deprecation = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "deprecation")
            .map(v -> (AnnotationMirror) v.getValue())
            .orElseThrow(() -> new AnnotationReadFailure(String.format("Missing 'deprecation' attribute in @%s", ReplacesEagerProperty.class.getSimpleName())));
        boolean enabled = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "enabled")
            .map(annotationValue -> (Boolean) annotationValue.getValue())
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'enabled' attribute in @ReplacedDeprecation"));
        RemovedIn removedIn = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "removedIn")
            .map(v -> RemovedIn.valueOf(v.getValue().toString()))
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'removedIn' attribute in @ReplacedDeprecation"));
        int withUpgradeGuideVersion = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withUpgradeGuideMajorVersion")
            .map(annotationValue -> (int) annotationValue.getValue())
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'withUpgradeGuideMajorVersion' attribute in @ReplacedDeprecation"));
        String withUpgradeGuideSection = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withUpgradeGuideSection")
            .map(annotationValue -> (String) annotationValue.getValue())
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'withUpgradeGuideSection' attribute in @ReplacedDeprecation"));
        boolean withDslReference = AnnotationUtils.findAnnotationValueWithDefaults(elements, deprecation, "withDslReference")
            .map(annotationValue -> (boolean) annotationValue.getValue())
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'withDslReference' attribute in @ReplacedDeprecation"));
        return new DeprecationSpec(enabled, removedIn, withUpgradeGuideVersion, withUpgradeGuideSection, withDslReference);
    }

    private BinaryCompatibility readBinaryCompatibility(AnnotationMirror annotation) {
        return AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "binaryCompatibility")
            .map(v -> BinaryCompatibility.valueOf(v.getValue().toString()))
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'binaryCompatibility' attribute in @ReplacedAccessor"));
    }

    private AccessorSpec getAccessorSpec(ExecutableElement method, AnnotationMirror annotation, DeprecationSpec parentDeprecationSpec, BinaryCompatibility binaryCompatibility) {
        String methodName = AnnotationUtils.findAnnotationValue(annotation, "name")
            .map(v -> (String) v.getValue())
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'name' attribute in @ReplacedAccessor"));
        AccessorType accessorType = AnnotationUtils.findAnnotationValue(annotation, "value")
            .map(v -> AccessorType.valueOf(v.getValue().toString()))
            .orElseThrow(() -> new AnnotationReadFailure("Missing 'value' attribute in @ReplacedAccessor"));
        Type originalType = extractOriginalType(method, annotation);
        return getAccessorSpec(method, accessorType, methodName, originalType, annotation, parentDeprecationSpec, binaryCompatibility);
    }

    private AccessorSpec getAccessorSpec(ExecutableElement method, AccessorType accessorType, AnnotationMirror annotation) {
        String propertyName = getPropertyName(method);
        Type originalType = extractOriginalType(method, annotation);
        String methodName;
        switch (accessorType) {
            case GETTER:
                String capitalize = propertyName.substring(0, 1).toUpperCase(Locale.ROOT) + propertyName.substring(1);
                methodName = originalType.equals(Type.BOOLEAN_TYPE) ? "is" + capitalize : "get" + capitalize;
                break;
            case SETTER:
                methodName = method.getSimpleName().toString().replaceFirst("get", "set");
                break;
            default:
                throw new IllegalArgumentException("Unsupported accessor type: " + accessorType);
        }
        DeprecationSpec deprecationSpec = readDeprecationSpec(annotation);
        BinaryCompatibility binaryCompatibility = readBinaryCompatibility(annotation);
        return getAccessorSpec(method, accessorType, methodName, originalType, annotation, deprecationSpec, binaryCompatibility);
    }

    private AccessorSpec getAccessorSpec(
        ExecutableElement method,
        AccessorType accessorType,
        String methodName,
        Type originalType,
        AnnotationMirror annotation,
        DeprecationSpec deprecationSpec,
        BinaryCompatibility binaryCompatibility
    ) {
        Type returnType;
        List<ParameterInfo> parameters;
        switch (accessorType) {
            case GETTER:
                parameters = new ArrayList<>();
                returnType = originalType;
                break;
            case SETTER:
                parameters = Collections.singletonList(new ParameterInfoImpl("arg0", originalType, METHOD_PARAMETER));
                boolean isFluentSetter = AnnotationUtils.findAnnotationValueWithDefaults(elements, annotation, "fluentSetter")
                    .map(v -> (Boolean) v.getValue())
                    .orElseThrow(() -> new AnnotationReadFailure("Missing 'fluentSetter' attribute"));
                returnType = isFluentSetter ? extractType(method.getEnclosingElement().asType()) : Type.VOID_TYPE;
                break;
            default:
                throw new IllegalArgumentException("Unsupported accessor type: " + accessorType);
        }
        String propertyName = getPropertyName(methodName);
        String generatedClassName = "org.gradle.internal.classpath.generated." + method.getEnclosingElement().getSimpleName() + "_Adapter";
        return new AccessorSpec(
            generatedClassName,
            accessorType,
            propertyName,
            methodName,
            returnType,
            parameters,
            deprecationSpec,
            binaryCompatibility,
            null
        );
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
        GradleLazyType gradleLazyType = GradleLazyType.from(typeName);
        switch (gradleLazyType) {
            case CONFIGURABLE_FILE_COLLECTION:
                return FILE_COLLECTION.asType();
            case DIRECTORY_PROPERTY:
            case REGULAR_FILE_PROPERTY:
                return Type.getType(File.class);
            case LIST_PROPERTY:
                return Type.getType(List.class);
            case SET_PROPERTY:
                return Type.getType(Set.class);
            case MAP_PROPERTY:
                return Type.getType(Map.class);
            case PROPERTY:
                return extractType(((DeclaredType) typeMirror).getTypeArguments().get(0));
            default:
                throw new AnnotationReadFailure(String.format("Cannot extract original type for method '%s.%s: %s'. Use explicit @%s#originalType instead.", method.getEnclosingElement(), method, typeMirror, ReplacesEagerProperty.class.getSimpleName()));
        }
    }

    private CallInterceptionRequest createGroovyPropertyInterceptionRequest(AccessorSpec accessor, ExecutableElement method) {
        String interceptorsClassName = getGroovyInterceptorsClassName();
        List<RequestExtra> extras = Arrays.asList(new RequestExtra.OriginatingElement(method), new RequestExtra.InterceptGroovyCalls(interceptorsClassName, BYTECODE_UPGRADE));
        List<ParameterInfo> parameters = Collections.singletonList(new ParameterInfoImpl("receiver", extractType(method.getEnclosingElement().asType()), RECEIVER));
        Type returnType = accessor.returnType;
        return new CallInterceptionRequestImpl(
            extractCallableInfo(GROOVY_PROPERTY_GETTER, method, returnType, accessor.propertyName, parameters),
            extractImplementationInfo(accessor, method, returnType, accessor.methodName, "get", Collections.emptyList()),
            extras
        );
    }

    private CallInterceptionRequest createJvmGetterInterceptionRequest(AccessorSpec accessor, ExecutableElement method) {
        List<RequestExtra> extras = getJvmRequestExtras(accessor, method, accessor.binaryCompatibility);
        String callableName = accessor.methodName;
        Type returnType = accessor.returnType;
        return new CallInterceptionRequestImpl(
            extractCallableInfo(INSTANCE_METHOD, method, returnType, callableName, Collections.emptyList()),
            extractImplementationInfo(accessor, method, returnType, accessor.methodName, "get", Collections.emptyList()),
            extras
        );
    }

    private CallInterceptionRequest createJvmSetterInterceptionRequest(AccessorSpec accessor, ExecutableElement method) {
        Type returnType = accessor.returnType;
        String callableName = accessor.methodName;
        List<ParameterInfo> parameters = accessor.parameters;
        BinaryCompatibility binaryCompatibility = accessor.binaryCompatibility;
        List<RequestExtra> extras = getJvmRequestExtras(accessor, method, binaryCompatibility);
        return new CallInterceptionRequestImpl(
            extractCallableInfo(INSTANCE_METHOD, method, returnType, callableName, parameters),
            extractImplementationInfo(accessor, method, returnType, accessor.methodName, "set", parameters),
            extras
        );
    }

    @Nonnull
    private List<RequestExtra> getJvmRequestExtras(AccessorSpec accessor, ExecutableElement method, BinaryCompatibility binaryCompatibility) {
        String interceptorsClassName = getJavaInterceptorsClassName();
        List<RequestExtra> extras = new ArrayList<>();
        extras.add(new RequestExtra.OriginatingElement(method));
        extras.add(new RequestExtra.InterceptJvmCalls(interceptorsClassName, BYTECODE_UPGRADE));
        String implementationClass = accessor.generatedClassName;
        GradleLazyType gradleLazyType = GradleLazyType.from(extractType(method.getReturnType()));
        String propertyName = getPropertyName(method);
        String methodDescriptor = extractMethodDescriptor(method);
        extras.add(new PropertyUpgradeRequestExtra(
            propertyName,
            method.getSimpleName().toString(),
            methodDescriptor,
            accessor.returnType,
            implementationClass,
            accessor.propertyName,
            accessor.methodName,
            gradleLazyType,
            accessor.deprecationSpec,
            binaryCompatibility,
            accessor.bridgedMethod
        ));
        return extras;
    }

    private static CallableInfo extractCallableInfo(CallableKindInfo kindInfo, ExecutableElement methodElement, Type returnType, String callableName, List<ParameterInfo> parameters) {
        CallableOwnerInfo owner = new CallableOwnerInfo(extractType(methodElement.getEnclosingElement().asType()), true);
        CallableReturnTypeInfo returnTypeInfo = new CallableReturnTypeInfo(returnType);
        return new CallableInfoImpl(kindInfo, owner, callableName, returnTypeInfo, parameters);
    }

    private static ImplementationInfoImpl extractImplementationInfo(AccessorSpec accessor, ExecutableElement method, Type returnType, String interceptedMethodName, String methodPrefix, List<ParameterInfo> parameters) {
        Type owner = extractType(method.getEnclosingElement().asType());
        Type implementationOwner = Type.getObjectType(accessor.generatedClassName);
        String implementationName = "access_" + methodPrefix + "_" + interceptedMethodName;
        String implementationDescriptor = Type.getMethodDescriptor(returnType, toArray(owner, parameters));
        return new ImplementationInfoImpl(implementationOwner, implementationName, implementationDescriptor);
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
        return getPropertyName(method.getSimpleName().toString());
    }

    private static boolean isGroovyProperty(String methodName) {
        return methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3));
    }

    private static String getPropertyName(String methodName) {
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        } else if ((methodName.startsWith("get") || methodName.startsWith("set")) && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else {
            return methodName;
        }
    }

    // TODO Consolidate with AnnotationCallInterceptionRequestReaderImpl#Failure
    private static class AnnotationReadFailure extends RuntimeException {
        final String reason;

        private AnnotationReadFailure(String reason) {
            this.reason = reason;
        }
    }

    private static class AccessorSpec {
        private final String generatedClassName;
        private final String propertyName;
        private final AccessorType accessorType;
        private final String methodName;
        private final Type returnType;
        private final List<ParameterInfo> parameters;
        private final BinaryCompatibility binaryCompatibility;
        private final DeprecationSpec deprecationSpec;
        private final ExecutableElement bridgedMethod;

        private AccessorSpec(
            String generatedClassName,
            AccessorType accessorType,
            String propertyName,
            String methodName,
            Type returnType,
            List<ParameterInfo> parameters,
            DeprecationSpec deprecationSpec,
            BinaryCompatibility binaryCompatibility,
            @Nullable ExecutableElement bridgedMethod
        ) {
            this.generatedClassName = generatedClassName;
            this.propertyName = propertyName;
            this.accessorType = accessorType;
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameters = parameters;
            this.deprecationSpec = deprecationSpec;
            this.binaryCompatibility = binaryCompatibility;
            this.bridgedMethod = bridgedMethod;
        }
    }

    static class DeprecationSpec {
        private final boolean enabled;
        private final RemovedIn removedIn;
        private final int withUpgradeGuideVersion;
        private final String withUpgradeGuideSection;
        private final boolean withDslReference;

        private DeprecationSpec(
            boolean enabled,
            RemovedIn removedIn,
            int withUpgradeGuideVersion,
            String withUpgradeGuideSection,
            boolean withDslReference
        ) {
            this.enabled = enabled;
            this.removedIn = removedIn;
            this.withUpgradeGuideVersion = withUpgradeGuideVersion;
            this.withUpgradeGuideSection = withUpgradeGuideSection;
            this.withDslReference = withDslReference;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public RemovedIn getRemovedIn() {
            return removedIn;
        }

        public int getWithUpgradeGuideVersion() {
            return withUpgradeGuideVersion;
        }

        public String getWithUpgradeGuideSection() {
            return withUpgradeGuideSection;
        }

        public boolean isWithDslReference() {
            return withDslReference;
        }
    }
}
