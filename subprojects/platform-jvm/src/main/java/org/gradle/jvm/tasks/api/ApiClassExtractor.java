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

class ApiClassExtractor {

    // See JLS3 "Binary Compatibility" (13.1)
    private static final Pattern LOCAL_CLASS_PATTERN = Pattern.compile(".+\\$[0-9]+(?:[\\p{Alnum}_$]+)?$");

    private final Set<String> exportedPackages;
    private final boolean apiIncludesPackagePrivateMembers;

    public ApiClassExtractor(Set<String> exportedPackages) {
        this.exportedPackages = exportedPackages;
        this.apiIncludesPackagePrivateMembers = exportedPackages.isEmpty();
    }

    /**
     * Returns true if the binary class found in parameter is belonging to the API. It will check if the class package is in the list of exported packages, and if the access flags are ok with
     * regards to the list of packages: if the list is empty, then package private classes are included, whereas if the list is not empty, an API has been declared and the class should be excluded.
     * Therefore, this method should be called on every .class file to process before it is either copied or processed through {@link #extractApiClassFrom(File)}.
     *
     * @param originalClassFile the file containing the original class to evaluate
     * @return whether the given class is a candidate for API extraction
     */
    public boolean shouldExtractApiClassFrom(File originalClassFile) throws IOException {
        if (!originalClassFile.getName().endsWith(".class")) {
            return false;
        }
        InputStream inputStream = new FileInputStream(originalClassFile);
        ClassReader classReader = new ClassReader(inputStream);
        try {
            return shouldExtractApiClassFrom(classReader);
        } finally {
            inputStream.close();
        }
    }

    boolean shouldExtractApiClassFrom(ClassReader originalClassReader) {
        final AtomicBoolean shouldExtract = new AtomicBoolean();
        originalClassReader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                String originalClassName = AsmUtils.convertInternalNameToClassName(name);
                shouldExtract.set(isApiClassExtractionCandidate(access, originalClassName));
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return shouldExtract.get();
    }

    /**
     * Extracts an API class from a given original class.
     *
     * @param originalClassFile the file containing the original class
     * @return bytecode of the API class extracted from the original class
     */
    public byte[] extractApiClassFrom(File originalClassFile) throws IOException {
        InputStream inputStream = new FileInputStream(originalClassFile);
        try {
            ClassReader classReader = new ClassReader(inputStream);
            return extractApiClassFrom(classReader);
        } finally {
            inputStream.close();
        }
    }

    byte[] extractApiClassFrom(ClassReader originalClassReader) {
        ClassWriter apiClassWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        originalClassReader.accept(new ApiMemberSelector(new MethodStubbingApiMemberAdapter(apiClassWriter), apiIncludesPackagePrivateMembers), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return apiClassWriter.toByteArray();
    }

    private boolean isApiClassExtractionCandidate(int access, String candidateClassName) {
        if (isLocalClass(candidateClassName)) {
            return false;
        }
        if (!ApiMemberSelector.isCandidateApiMember(access, apiIncludesPackagePrivateMembers)) {
            return false;
        }
        if (exportedPackages.isEmpty()) {
            return true;
        }
        String packageName = candidateClassName.indexOf('.') > 0 ? candidateClassName.substring(0, candidateClassName.lastIndexOf('.')) : "";
        for (String exportedPackage : exportedPackages) {
            if (packageName.equals(exportedPackage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalClass(String className) {
        return LOCAL_CLASS_PATTERN.matcher(className).matches();
    }
}
