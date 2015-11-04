/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.tasks.api;

import com.google.common.collect.Sets;
import org.gradle.internal.Factory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.Collections;
import java.util.Set;

import static org.gradle.jvm.tasks.api.AsmUtils.convertInternalNameToClassName;

class DefaultApiValidator implements ApiValidator {
    private final MemberOfApiChecker memberOfApiChecker;

    public DefaultApiValidator(MemberOfApiChecker memberOfApiChecker) {
        this.memberOfApiChecker = memberOfApiChecker;
    }

    @Override
    public <T> T validateMethod(MethodSig methodSig, Factory<T> onValidate) {
        Set<String> invalidReferencedTypes = invalidReferencedTypes(methodSig.getSignature() == null ? methodSig.getDesc() : methodSig.getSignature());
        for (String exception : methodSig.getExceptions()) {
            invalidReferencedTypes = Sets.union(invalidReferencedTypes, invalidReferencedTypes(exception));
        }
        if (invalidReferencedTypes.isEmpty()) {
            return onValidate.create();
        }
        if (invalidReferencedTypes.size() == 1) {
            throw new InvalidApiException(String.format("In %s, type %s is exposed in the public API but its package is not one of the allowed packages.", methodSig, invalidReferencedTypes.iterator().next()));
        } else {
            StringBuilder sb = new StringBuilder("The following types are referenced in ");
            sb.append(methodSig);
            sb.append(" but their package is not one of the allowed packages:\n");
            for (String invalidReferencedType : invalidReferencedTypes) {
                sb.append("   - ").append(invalidReferencedType).append("\n");
            }
            throw new InvalidApiException(sb.toString());
        }
    }

    @Override
    public <T> T validateField(FieldSig fieldSig, Factory<T> onValidate) {
        Set<String> invalidReferencedTypes = invalidReferencedTypes(fieldSig.getSignature());
        if (invalidReferencedTypes.isEmpty()) {
            return onValidate.create();
        }
        if (invalidReferencedTypes.size() == 1) {
            throw new InvalidApiException(String.format("Field '%s' references disallowed API type '%s'", fieldSig, invalidReferencedTypes.iterator().next()));
        } else {
            StringBuilder sb = new StringBuilder("The following types are referenced in ");
            sb.append(fieldSig);
            sb.append(" but their package is not one of the allowed packages:\n");
            for (String invalidReferencedType : invalidReferencedTypes) {
                sb.append("   - ").append(invalidReferencedType).append("\n");
            }
            throw new InvalidApiException(sb.toString());
        }
    }

    @Override
    public <T> T validateAnnotation(String owner, String annotationDesc, Factory<T> onValidate) {
        String annotation = Type.getType(annotationDesc).getClassName();
        if (memberOfApiChecker.belongsToApi(annotation)) {
            return onValidate.create();
        }
        throw new InvalidApiException(String.format("'%s' is annotated with '%s' effectively exposing it in the public API but its package is not one of the allowed packages.", owner, annotation));
    }

    @Override
    public void validateSuperTypes(String name, String signature, String superName, String[] interfaces) {
        String className = convertInternalNameToClassName(name);
        String superClassName = convertInternalNameToClassName(superName);
        if (!memberOfApiChecker.belongsToApi(superClassName)) {
            throw new InvalidApiException(String.format("'%s' extends '%s' and its package is not one of the allowed packages.", className, superClassName));
        }
        Set<String> invalidReferencedTypes = invalidReferencedTypes(signature);
        if (!invalidReferencedTypes.isEmpty()) {
            if (invalidReferencedTypes.size() == 1) {
                throw new InvalidApiException(String.format("'%s' references disallowed API type '%s' in superclass or interfaces.", className, invalidReferencedTypes.iterator().next()));
            } else {
                StringBuilder sb = new StringBuilder("The following types are referenced in ");
                sb.append(className);
                sb.append(" superclass but their package don't belong to the allowed packages:\n");
                for (String invalidReferencedType : invalidReferencedTypes) {
                    sb.append("   - ").append(invalidReferencedType).append("\n");
                }
                throw new InvalidApiException(sb.toString());
            }
        }
        if (interfaces != null) {
            for (String intf : interfaces) {
                String interfaceName = convertInternalNameToClassName(intf);
                if (!memberOfApiChecker.belongsToApi(interfaceName)) {
                    throw new InvalidApiException(String.format("'%s' declares interface '%s' and its package is not one of the allowed packages.", className, interfaceName));
                }
            }
        }
    }

    protected Set<String> invalidReferencedTypes(String signature) {
        if (signature == null) {
            return Collections.emptySet();
        }
        SignatureReader sr = new SignatureReader(signature);
        final Set<String> result = Sets.newLinkedHashSet();
        sr.accept(new SignatureVisitor(Opcodes.ASM5) {
            @Override
            public void visitClassType(String name) {
                super.visitClassType(name);
                String className = convertInternalNameToClassName(name);
                if (!memberOfApiChecker.belongsToApi(className)) {
                    result.add(className);
                }
            }
        });
        return result;
    }
}
