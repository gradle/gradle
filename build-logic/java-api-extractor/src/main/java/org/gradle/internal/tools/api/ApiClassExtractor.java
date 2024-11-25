/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.tools.api;

import org.gradle.internal.tools.api.impl.ApiMemberSelector;
import org.gradle.internal.tools.api.impl.MethodStubbingApiMemberAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Extracts an "API class" from an original "runtime class".
 */
public class ApiClassExtractor {

    private static final Pattern LOCAL_CLASS_PATTERN = Pattern.compile(".+\\$[0-9]+(?:[\\p{Alnum}_$]+)?$");

    private final Predicate<String> packageNameFilter;
    private final boolean apiIncludesPackagePrivateMembers;
    private final ApiMemberWriterFactory apiMemberWriterFactory;

    public static class Builder {
        private final ApiMemberWriterFactory apiMemberWriterFactory;
        private boolean includePackagePrivateMembers;
        private Predicate<String> packageNameFilter = name -> true;

        Builder(ApiMemberWriterFactory apiMemberWriterFactory) {
            this.apiMemberWriterFactory = apiMemberWriterFactory;
        }

        public Builder includePackagesMatching(Predicate<String> packageNameFilter) {
            this.packageNameFilter = packageNameFilter;
            return this;
        }

        public Builder includePackagePrivateMembers() {
            this.includePackagePrivateMembers = true;
            return this;
        }

        public ApiClassExtractor build() {
            return new ApiClassExtractor(packageNameFilter, includePackagePrivateMembers, apiMemberWriterFactory);
        }
    }

    public static Builder withWriter(ApiMemberWriterAdapter writerCreator) {
        return new Builder(classWriter -> writerCreator.createWriter(new MethodStubbingApiMemberAdapter(classWriter)));
    }

    private ApiClassExtractor(Predicate<String> packageNameFilter, boolean includePackagePrivateMembers, ApiMemberWriterFactory apiMemberWriterFactory) {
        this.packageNameFilter = packageNameFilter;
        this.apiIncludesPackagePrivateMembers = includePackagePrivateMembers;
        this.apiMemberWriterFactory = apiMemberWriterFactory;
        System.out.println(">>> USING THE NEW THING");
    }

    /**
     * Extracts an API class from a given original class.
     *
     * <p>Checks whether the class's package is in the list of packages
     * explicitly exported by the library (if any), and whether the class should be
     * included in the public API based on its visibility. If the list of exported
     * packages is empty (e.g. the library has not declared an explicit {@code api {...}}
     * specification, then package-private classes are included in the public API. If the
     * list of exported packages is non-empty (i.e. the library has declared an
     * {@code api {...}} specification, then package-private classes are excluded.</p>
     *
     * @param originalClassBytes the bytecode of the original class
     * @return bytecode of the API class extracted from the original class. Returns {@link Optional#empty()} when class should not be included, due to some reason that is not known until visited.
     */
    public Optional<byte[]> extractApiClassFrom(byte[] originalClassBytes) {
        ClassReader originalClassReader = new ClassReader(originalClassBytes);
        return extractApiClassFromReader(originalClassReader)
            .map(ClassWriter::toByteArray);
    }

    protected Optional<ClassWriter> extractApiClassFromReader(ClassReader originalClassReader) {
        if (!shouldExtractApiClassFrom(originalClassReader)) {
            return Optional.empty();
        }
        ClassWriter apiClassWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ApiMemberSelector visitor;
        try {
            visitor = new ApiMemberSelector(originalClassReader.getClassName(), apiMemberWriterFactory.makeApiMemberWriter(apiClassWriter), apiIncludesPackagePrivateMembers);
            originalClassReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (ApiClassExtractionException e) {
            throw e.withClass(originalClassReader.getClassName());
        }
        if (visitor.isPrivateInnerClass()) {
            return Optional.empty();
        }
        return Optional.of(apiClassWriter);
    }

    private boolean shouldExtractApiClassFrom(ClassReader originalClassReader) {
        if (!ApiMemberSelector.isCandidateApiMember(originalClassReader.getAccess(), apiIncludesPackagePrivateMembers)) {
            return false;
        }
        String originalClassName = originalClassReader.getClassName();
        if (isLocalClass(originalClassName)) {
            return false;
        }
        return packageNameFilter.test(packageNameOf(originalClassName));
    }

    private static String packageNameOf(String internalClassName) {
        int packageSeparatorIndex = internalClassName.lastIndexOf('/');
        return packageSeparatorIndex > 0
            ? internalClassName.substring(0, packageSeparatorIndex).replace('/', '.')
            : "";
    }

    // See JLS3 "Binary Compatibility" (13.1)
    private static boolean isLocalClass(String className) {
        return LOCAL_CLASS_PATTERN.matcher(className).matches();
    }
}
