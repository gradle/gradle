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
        return builder -> builder.addModifiers(Modifier.PUBLIC).addMethods(methods);
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
        switch (implementationExtra.getUpgradedPropertyType()) {
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
        String propertyGetterName = implementationExtra.getInterceptedPropertyAccessorName();
        boolean isSetter = implementation.getName().startsWith("access_set_");
        CallableReturnTypeInfo returnType = callableInfo.getReturnType();
        GradleLazyType upgradedPropertyType = implementationExtra.getUpgradedPropertyType();
        if (isSetter) {
            String setCall = getSetCall(upgradedPropertyType);
            if (implementationExtra.isFluentSetter()) {
                return CodeBlock.of("$N.$N()$N;\nreturn $N;", SELF_PARAMETER_NAME, propertyGetterName, setCall, SELF_PARAMETER_NAME);
            } else {
                return CodeBlock.of("$N.$N()$N;", SELF_PARAMETER_NAME, propertyGetterName, setCall);
            }
        } else {
            return getGetCall(propertyGetterName, returnType, upgradedPropertyType);
        }
    }

    private static CodeBlock getGetCall(String propertyGetterName, CallableReturnTypeInfo returnType, GradleLazyType upgradedPropertyType) {
        switch (upgradedPropertyType) {
            case REGULAR_FILE_PROPERTY:
            case DIRECTORY_PROPERTY:
                return CodeBlock.of("return $N.$N().getAsFile().getOrNull();", SELF_PARAMETER_NAME, propertyGetterName);
            case CONFIGURABLE_FILE_COLLECTION:
            case FILE_COLLECTION:
                return CodeBlock.of("return $N.$N();", SELF_PARAMETER_NAME, propertyGetterName);
            case LIST_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N());", LIST_PROPERTY_LIST_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case SET_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N());", SET_PROPERTY_SET_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case MAP_PROPERTY:
                return CodeBlock.of("return new $T<>($N.$N());", MAP_PROPERTY_MAP_VIEW.asTypeName(), SELF_PARAMETER_NAME, propertyGetterName);
            case PROPERTY:
                return CodeBlock.of("return $N.$N().getOrElse($L);", SELF_PARAMETER_NAME, propertyGetterName, TypeUtils.getDefaultValue(returnType.getType()));
            default:
                throw new UnsupportedOperationException("Generating get call for type: " + upgradedPropertyType.asType() + " is not supported");
        }
    }

    private static String getSetCall(GradleLazyType upgradedPropertyType) {
        switch (upgradedPropertyType) {
            case REGULAR_FILE_PROPERTY:
            case DIRECTORY_PROPERTY:
                return ".fileValue(arg0)";
            case CONFIGURABLE_FILE_COLLECTION:
                return ".setFrom(arg0)";
            case LIST_PROPERTY:
            case SET_PROPERTY:
            case MAP_PROPERTY:
            case PROPERTY:
                return ".set(arg0)";
            default:
                throw new UnsupportedOperationException("Generating set call for type: " + upgradedPropertyType.asType() + " is not supported");
        }
    }
}
