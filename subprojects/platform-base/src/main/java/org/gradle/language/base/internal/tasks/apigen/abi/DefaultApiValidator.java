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
package org.gradle.language.base.internal.tasks.apigen.abi;

import com.google.common.collect.Sets;
import org.gradle.internal.Factory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.Collections;
import java.util.Set;

public class DefaultApiValidator implements ApiValidator {
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
            throw new InvalidPublicAPIException(String.format("In %s, type %s is exposed in the public API but its package is not one of the allowed packages.", methodSig, invalidReferencedTypes.iterator().next()));
        } else {
            StringBuilder sb = new StringBuilder("The following types are referenced in ");
            sb.append(methodSig);
            sb.append(" but their package is not one of the allowed packages:\n");
            for (String invalidReferencedType : invalidReferencedTypes) {
                sb.append("   - ").append(invalidReferencedType).append("\n");
            }
            throw new InvalidPublicAPIException(sb.toString());
        }
    }

    @Override
    public <T> T validateField(FieldSig fieldSig, Factory<T> onValidate) {
        Set<String> invalidReferencedTypes = invalidReferencedTypes(fieldSig.getSignature());
        if (invalidReferencedTypes.isEmpty()) {
            return onValidate.create();
        }
        if (invalidReferencedTypes.size() == 1) {
            throw new InvalidPublicAPIException(String.format("Field '%s' references disallowed API type '%s'", fieldSig, invalidReferencedTypes.iterator().next()));
        } else {
            StringBuilder sb = new StringBuilder("The following types are referenced in ");
            sb.append(fieldSig);
            sb.append(" but their package is not one of the allowed packages:\n");
            for (String invalidReferencedType : invalidReferencedTypes) {
                sb.append("   - ").append(invalidReferencedType).append("\n");
            }
            throw new InvalidPublicAPIException(sb.toString());
        }
    }

    @Override
    public <T> T validateAnnotation(String owner, String annotationDesc, Factory<T> onValidate) {
        String annotation = Type.getType(annotationDesc).getClassName();
        if (memberOfApiChecker.belongsToApi(annotation)) {
            return onValidate.create();
        }
        throw new InvalidPublicAPIException(String.format("'%s' is annotated with '%s' effectively exposing it in the public API but its package is not one of the allowed packages.", owner, annotation));
    }

    public static String toClassName(String cn) {
        return cn.replace('/', '.');
    }

    @Override
    public void validateSuperTypes(String name, String signature, String superName, String[] interfaces) {
        if (!memberOfApiChecker.belongsToApi(toClassName(superName))) {
            throw new InvalidPublicAPIException(String.format("'%s' extends '%s' and its package is not one of the allowed packages.", toClassName(name), toClassName(superName)));
        }
        Set<String> invalidReferencedTypes = invalidReferencedTypes(signature);
        if (!invalidReferencedTypes.isEmpty()) {
            if (invalidReferencedTypes.size() == 1) {
                throw new InvalidPublicAPIException(String.format("'%s' references disallowed API type '%s' in superclass or interfaces.", toClassName(name), invalidReferencedTypes.iterator().next()));
            } else {
                StringBuilder sb = new StringBuilder("The following types are referenced in ");
                sb.append(toClassName(name));
                sb.append(" superclass but their package don't belong to the allowed packages:\n");
                for (String invalidReferencedType : invalidReferencedTypes) {
                    sb.append("   - ").append(invalidReferencedType).append("\n");
                }
                throw new InvalidPublicAPIException(sb.toString());
            }
        }
        if (interfaces != null) {
            for (String intf : interfaces) {
                if (!memberOfApiChecker.belongsToApi(toClassName(intf))) {
                    throw new InvalidPublicAPIException(String.format("'%s' declares interface '%s' and its package is not one of the allowed packages.", toClassName(name), toClassName(intf)));
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
                String className = toClassName(name);
                if (!memberOfApiChecker.belongsToApi(className)) {
                    result.add(className);
                }
            }
        });
        return result;
    }
}
