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

import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

class ApiUnitExtractor {

    // See JLS3 "Binary Compatibility" (13.1)
    private final static Pattern AIC_LOCAL_CLASS_PATTERN = Pattern.compile(".+\\$[0-9]+(?:[\\p{Alnum}_$]+)?$");

    private final boolean hasDeclaredApi;
    private final MemberOfApiChecker memberOfApiChecker;

    public ApiUnitExtractor(Set<String> exportedPackages) {
        this.hasDeclaredApi = !exportedPackages.isEmpty();
        this.memberOfApiChecker = hasDeclaredApi ? new DefaultMemberOfApiChecker(exportedPackages) : new AlwaysMemberOfApiChecker();
    }

    /**
     * Returns true if the binary class found in parameter is belonging to the API. It will check if the class package is in the list of exported packages, and if the access flags are ok with
     * regards to the list of packages: if the list is empty, then package private classes are included, whereas if the list is not empty, an API has been declared and the class should be excluded.
     * Therefore, this method should be called on every .class file to process before it is either copied or processed through {@link #extractApiUnitFrom(File)}.
     *
     * @param compilationUnit the file containing the compilation unit to evaluate
     * @return whether the given compilation unit is a candidate for API extraction
     */
    public boolean shouldExtractApiUnitFrom(File compilationUnit) throws IOException {
        if (!compilationUnit.getName().endsWith(".class")) {
            return false;
        }
        InputStream inputStream = new FileInputStream(compilationUnit);
        ClassReader classReader = new ClassReader(inputStream);
        try {
            return shouldExtractApiUnitFrom(classReader);
        } finally {
            inputStream.close();
        }
    }

    boolean shouldExtractApiUnitFrom(ClassReader classReader) {
        final AtomicBoolean shouldExtract = new AtomicBoolean();
        classReader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String className = AsmUtils.convertInternalNameToClassName(name);
                shouldExtract.set(memberOfApiChecker.belongsToApi(className) && ApiMemberExtractor.isApiMember(access, hasDeclaredApi) && !AIC_LOCAL_CLASS_PATTERN.matcher(name).matches());
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return shouldExtract.get();
    }

    /**
     * Extracts an API compilation unit from a given original compilation unit.
     *
     * @param compilationUnit the file containing the compilation unit to extract
     * @return bytecode of the API compilation unit extracted from the original compilation unit
     */
    public byte[] extractApiUnitFrom(File compilationUnit) throws IOException {
        InputStream inputStream = new FileInputStream(compilationUnit);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            return extractApiUnitFrom(classReader);
        } finally {
            inputStream.close();
        }
    }

    byte[] extractApiUnitFrom(ClassReader cr) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ApiMemberExtractor(new MethodStubbingClassVisitor(cw), hasDeclaredApi), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }
}
