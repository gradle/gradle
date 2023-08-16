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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.ParameterInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;
import org.gradle.internal.instrumentation.processor.codegen.TypeUtils;
import org.objectweb.asm.Type;

import javax.lang.model.element.Modifier;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates static final TargetType constants for {@code  CallInterceptor} implementations.
 * CallInterceptor uses TargetType in argument matching instead of, for example, {@code instanceof} to follow Groovy coercion rules.
 */
class TargetTypeFieldsGenerator implements TargetTypeFieldLookup {
    private static final ClassName TARGET_TYPE_CLASS = ClassName.bestGuess("org.gradle.internal.classpath.intercept.TargetType");

    private final Map<TypeName, String> fieldNames = new LinkedHashMap<>();

    public TargetTypeFieldsGenerator(List<CallInterceptionRequest> requests) {
        List<TypeName> typeNames = parametersStream(requests)
            .filter(TargetTypeFieldsGenerator::isUsedInTypeCasts)
            .map(TargetTypeFieldsGenerator::getTypeCastType)
            .sorted(Comparator.comparing(TypeName::toString)) // Arbitrary but stable order.
            .distinct()
            .collect(Collectors.toList());

        // The index is used to have unique names for types with the same simple name.
        for (int i = 0, l = typeNames.size(); i < l; ++i) {
            TypeName typeName = typeNames.get(i);
            fieldNames.put(typeName, "type" + i + toFieldNamePart(typeName));
        }
    }

    public Collection<FieldSpec> getFields() {
        return fieldNames.entrySet().stream().map(e ->
            FieldSpec.builder(
                    ParameterizedTypeName.get(TARGET_TYPE_CLASS, e.getKey()),
                    e.getValue(),
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                    "$T.of($T.class)", TARGET_TYPE_CLASS, e.getKey())
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    public CodeBlock lookupTargetTypeFieldFor(TypeName type) {
        return CodeBlock.of("$L", Objects.requireNonNull(fieldNames.get(type), () -> "Cannot find TargetType field for type " + type));
    }

    private static String toFieldNamePart(TypeName typeName) {
        if (typeName instanceof ClassName) {
            return ((ClassName) typeName).simpleName();
        }

        if (typeName instanceof ArrayTypeName) {
            return toFieldNamePart(((ArrayTypeName) typeName).componentType) + "Array";
        }

        // Fall back to "type0".
        return "";
    }

    private static Stream<ParameterInfo> parametersStream(List<CallInterceptionRequest> requests) {
        return requests.stream().flatMap(r -> r.getInterceptedCallable().getParameters().stream());
    }

    private static boolean isUsedInTypeCasts(ParameterInfo info) {
        return info.getKind() == ParameterKindInfo.RECEIVER || info.getKind().isSourceParameter();
    }

    private static TypeName getTypeCastType(ParameterInfo info) {
        Type type = info.getKind() == ParameterKindInfo.VARARG_METHOD_PARAMETER && info.getParameterType().getSort() == Type.ARRAY
            ? info.getParameterType().getElementType()
            : info.getParameterType();
        // Only boxed types can be type arguments of TargetType.
        return TypeUtils.typeName(type).box();
    }
}
