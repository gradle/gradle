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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.extensions.property.PropertyUpgradeAnnotatedMethodReader.DeprecationSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableReturnTypeInfo;
import org.gradle.internal.instrumentation.model.ImplementationInfo;
import org.gradle.internal.instrumentation.processor.codegen.GradleLazyType;
import org.gradle.internal.instrumentation.processor.codegen.HasFailures;
import org.gradle.internal.instrumentation.processor.codegen.RequestGroupingInstrumentationClassSourceGenerator;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;

import javax.lang.model.element.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.DEPRECATION_LOGGER;
import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.GENERATED_ANNOTATION;
import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.LIST_PROPERTY_LIST_VIEW;
import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.MAP_PROPERTY_MAP_VIEW;
import static org.gradle.internal.instrumentation.processor.codegen.GradleReferencedType.SET_PROPERTY_SET_VIEW;
import static org.gradle.internal.instrumentation.processor.codegen.TypeUtils.typeName;

public class PropertyUpgradeClassSourceGenerator extends RequestGroupingInstrumentationClassSourceGenerator {

    private static final String SELF_PARAMETER_NAME = "self";

    @Override
    protected String classNameForRequest(CallInterceptionRequest request) {
        return request.getRequestExtras().getByType(PropertyUpgradeRequestExtra.class)
            .map(PropertyUpgradeRequestExtra::getImplementationClassName)
            .orElse(null);
    }

    @Override
    protected Consumer<TypeSpec.Builder> classContentForClass(
        String className,
        List<CallInterceptionRequest> requestsClassGroup,
        Consumer<? super CallInterceptionRequest> onProcessedRequest,
        Consumer<? super HasFailures.FailureInfo> onFailure
    ) {
        List<MethodSpec> methods = requestsClassGroup.stream()
            .map(request -> mapToMethodSpec(request, onProcessedRequest, onFailure))
            .collect(Collectors.toList());

        return builder -> builder
            .addAnnotation(GENERATED_ANNOTATION.asClassName())
            .addModifiers(Modifier.PUBLIC)
            .addMethods(methods);
    }

    private static MethodSpec mapToMethodSpec(CallInterceptionRequest request, Consumer<? super CallInterceptionRequest> onProcessedRequest, Consumer<? super HasFailures.FailureInfo> onFailure) {
        PropertyUpgradeRequestExtra implementationExtra = request.getRequestExtras()
            .getByType(PropertyUpgradeRequestExtra.class)
            .orElseThrow(() -> new RuntimeException(PropertyUpgradeRequestExtra.class.getSimpleName() + " should be present at this stage!"));

        try {
            CallableInfo callable = request.getInterceptedCallable();
            ImplementationInfo implementation = request.getImplementationInfo();
            List<ParameterSpec> parameters = callable.getParameters().stream()
                .map(parameter -> ParameterSpec.builder(typeName(parameter.getParameterType()), parameter.getName()).build())
                .collect(Collectors.toList());
            MethodSpec spec = MethodSpec.methodBuilder(implementation.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(typeName(callable.getOwner().getType()), SELF_PARAMETER_NAME)
                .addParameters(parameters)
                .addCode(generateMethodBody(implementation, callable, implementationExtra))
                .returns(typeName(callable.getReturnType().getType()))
                .addAnnotations(getAnnotations(implementationExtra))
                .build();
            onProcessedRequest.accept(request);
            return spec;
        } catch (Exception e) {
            onFailure.accept(new HasFailures.FailureInfo(request, e.getMessage()));
            throw e;
        }

    }

    private static List<AnnotationSpec> getAnnotations(PropertyUpgradeRequestExtra implementationExtra) {
        switch (implementationExtra.getPropertyType()) {
            case LIST_PROPERTY:
            case SET_PROPERTY:
            case MAP_PROPERTY:
                return Collections.singletonList(AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$L", "{\"unchecked\", \"rawtypes\"}")
                    .build());
            default:
                return Collections.emptyList();
        }
    }

    private static CodeBlock generateMethodBody(ImplementationInfo implementation, CallableInfo callableInfo, PropertyUpgradeRequestExtra implementationExtra) {
        String propertyGetterName = implementationExtra.getMethodName();
        boolean isSetter = implementation.getName().startsWith("access_set_");
        CallableReturnTypeInfo returnType = callableInfo.getReturnType();
        GradleLazyType upgradedPropertyType = implementationExtra.getPropertyType();

        CodeBlock.Builder codeBlockBuilder = CodeBlock.builder();
        if (implementationExtra.getDeprecationSpec().isEnabled()) {
            codeBlockBuilder.addStatement(getDeprecationCodeBlock(implementationExtra, callableInfo));
        }

        CodeBlock logic = isSetter
            ? getSetCall(propertyGetterName, implementationExtra, upgradedPropertyType)
            : getGetCall(propertyGetterName, returnType, upgradedPropertyType);

        return codeBlockBuilder.addStatement(logic).build();
    }

    private static CodeBlock getDeprecationCodeBlock(PropertyUpgradeRequestExtra requestExtra, CallableInfo callableInfo) {
        String newPropertyName = requestExtra.getPropertyName();
        String deprecatedPropertyName = requestExtra.getInterceptedPropertyName();
        DeprecationSpec deprecationSpec = requestExtra.getDeprecationSpec();

        CodeBlock.Builder depreactionBuilder = CodeBlock.builder()
            .add("$T.deprecateProperty($T.class, $S)\n", DEPRECATION_LOGGER.asTypeName(), TypeUtils.typeName(callableInfo.getOwner().getType()), deprecatedPropertyName)
            .add(".withContext($S)\n", "Property was automatically upgraded to the lazy version.");

        if (!newPropertyName.equals(deprecatedPropertyName)) {
            depreactionBuilder.add(".replaceWith($S)\n", newPropertyName);
        }

        switch (deprecationSpec.getRemovedIn()) {
            case GRADLE9:
                depreactionBuilder.add(".willBeRemovedInGradle9()\n");
                break;
            case UNSPECIFIED:
                depreactionBuilder.add(".startingWithGradle9($S)\n", "this property is replaced with a lazy version");
                break;
            default:
                throw new UnsupportedOperationException("Only unset or 9 is currently supported for removedIn, but was: " + deprecationSpec.getRemovedIn());
        }

        if (deprecationSpec.getWithUpgradeGuideVersion() != -1) {
            depreactionBuilder.add(".withUpgradeGuideSection($L, $S)\n", deprecationSpec.getWithUpgradeGuideVersion(), deprecationSpec.getWithUpgradeGuideSection());
        } else if (deprecationSpec.isWithDslReference()) {
            depreactionBuilder.add(".withDslReference()\n");
        } else {
            depreactionBuilder.add(".undocumented()\n");
        }

        return depreactionBuilder.add(".nagUser()")
            .build();
    }

    private static CodeBlock getGetCall(String propertyGetterName, CallableReturnTypeInfo returnType, GradleLazyType upgradedPropertyType) {
        switch (upgradedPropertyType) {
            case REGULAR_FILE_PROPERTY:
            case DIRECTORY_PROPERTY:
                return CodeBlock.of("return $N.$N().getAsFile().getOrNull()", SELF_PARAMETER_NAME, propertyGetterName);
            case CONFIGURABLE_FILE_COLLECTION:
            case FILE_COLLECTION:
                return CodeBlock.of("return $N.$N()", SELF_PARAMETER_NAME, propertyGetterName);
            case LIST_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N())", LIST_PROPERTY_LIST_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case SET_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N())", SET_PROPERTY_SET_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case MAP_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N())", MAP_PROPERTY_MAP_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case PROPERTY:
                return CodeBlock.of("return $N.$N().getOrElse($L)", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnType.getType()));
            default:
                throw new UnsupportedOperationException("Generating get call for type: " + upgradedPropertyType.asType() + " is not supported");
        }
    }

    private static CodeBlock getSetCall(String propertyGetterName, PropertyUpgradeRequestExtra implementationExtra, GradleLazyType upgradedPropertyType) {
        String assignment;
        switch (upgradedPropertyType) {
            case REGULAR_FILE_PROPERTY:
            case DIRECTORY_PROPERTY:
                assignment = ".fileValue(arg0)";
                break;
            case CONFIGURABLE_FILE_COLLECTION:
                assignment = ".setFrom(arg0)";
                break;
            case LIST_PROPERTY:
            case SET_PROPERTY:
            case MAP_PROPERTY:
            case PROPERTY:
                assignment = ".set(arg0)";
                break;
            default:
                throw new UnsupportedOperationException("Generating set call for type: " + upgradedPropertyType.asType() + " is not supported");
        }
        if (implementationExtra.isFluentSetter()) {
            return CodeBlock.of("$N.$N()$N;\nreturn $N", SELF_PARAMETER_NAME, propertyGetterName, assignment, SELF_PARAMETER_NAME);
        } else {
            return CodeBlock.of("$N.$N()$N", SELF_PARAMETER_NAME, propertyGetterName, assignment);
        }
    }
}
