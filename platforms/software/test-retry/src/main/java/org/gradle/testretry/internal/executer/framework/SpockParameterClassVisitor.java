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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.testretry.internal.testsreader.TestsReader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * Class visitor that identifies unparameterized test method names.
 */
final class SpockParameterClassVisitor extends TestsReader.Visitor<Map<String, List<String>>> {

    // A valid Java identifier https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.8 including methods
    private static final String SPOCK_PARAM_PATTERN = "#[\\p{L}\\d$_.()&&[^#\\s]]+";
    private static final String WILDCARD = ".*";

    private final Set<String> failedTestNames;
    private final TestsReader testsReader;
    private final Map<String, SpockParameterMethodVisitor> spockMethodVisitorByMethodName = new HashMap<>();
    private boolean isSpec;

    public SpockParameterClassVisitor(Set<String> testMethodName, TestsReader testsReader) {
        this.failedTestNames = testMethodName;
        this.testsReader = testsReader;
    }

    @Override
    public Map<String, List<String>> getResult() {
        if (!isSpec) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> map = new HashMap<>();
        spockMethodVisitorByMethodName.values().stream()
            .filter(SpockParameterMethodVisitor::isSpockTestMethod)
            .forEach(spockMethodVisitor -> {
                Optional<String> unrollTemplate = spockMethodVisitor.getUnrollTemplate();
                if (unrollTemplate.isPresent()) {
                    // if failed tests match the unroll template, we rerun the declared test method
                    addMatchingMethodForFailedTests(map, unrollTemplate.get(), spockMethodVisitor.getTestMethodName());
                } else {
                    // if failed tests match the declared test method name/template, we rerun the declared test method
                    addMatchingMethodForFailedTests(map, spockMethodVisitor.getTestMethodName(), spockMethodVisitor.getTestMethodName());
                }
            });

        return map;
    }

    private void addMatchingMethodForFailedTests(Map<String, List<String>> matchingMethodsPerFailedTest, String methodPattern, String methodName) {
        // Replace params in the method name with .*
        String methodPatternRegex = Arrays.stream(methodPattern.split(SPOCK_PARAM_PATTERN))
            .map(Pattern::quote)
            .collect(Collectors.joining(WILDCARD))
            + WILDCARD; // For when no params in name - [iterationNum] implicitly added to end

        failedTestNames.forEach(failedTestName -> {
            List<String> matches = matchingMethodsPerFailedTest.computeIfAbsent(failedTestName, ignored -> new ArrayList<>());
            if (methodPattern.equals(failedTestName) || failedTestName.matches(methodPatternRegex)) {
                matches.add(methodName);
            }
        });
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        if (superName != null) {
            if (superName.equals("spock/lang/Specification")) {
                isSpec = true;
            } else if (!superName.equals("java/lang/Object")) {
                testsReader.readClass(superName.replace('/', '.'), () -> this);
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return isSpec ? spockMethodVisitorByMethodName.computeIfAbsent(name, __ -> new SpockParameterMethodVisitor()) : null;
    }

    private static final class SpockParameterMethodVisitor extends MethodVisitor {

        @Nullable
        private SpockFeatureMetadataAnnotationVisitor featureMethodAnnotationVisitor;
        @Nullable
        private SpockUnrollAnnotationVisitor unrollAnnotationVisitor;

        public SpockParameterMethodVisitor() {
            super(ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.contains("org/spockframework/runtime/model/FeatureMetadata")) {
                featureMethodAnnotationVisitor = new SpockFeatureMetadataAnnotationVisitor();
                return featureMethodAnnotationVisitor;
            }
            if (descriptor.contains("spock/lang/Unroll")) {
                unrollAnnotationVisitor = new SpockUnrollAnnotationVisitor();
                return unrollAnnotationVisitor;
            }
            return null;
        }

        public boolean isSpockTestMethod() {
            return featureMethodAnnotationVisitor != null;
        }

        public String getTestMethodName() {
            return requireNonNull(requireNonNull(featureMethodAnnotationVisitor).testMethodName);
        }

        public Optional<String> getUnrollTemplate() {
            return Optional.ofNullable(unrollAnnotationVisitor).map(visitor -> visitor.unrollTemplate);
        }

        /**
         * Looking for signatures like:
         * org/spockframework/runtime/model/FeatureMetadata;(
         * line=15,
         * name="unrolled with param #param",
         * ordinal=0,
         * blocks={...},
         * parameterNames={"param", "result"}
         * )
         */
        private static final class SpockFeatureMetadataAnnotationVisitor extends AnnotationVisitor {

            private String testMethodName;

            public SpockFeatureMetadataAnnotationVisitor() {
                super(ASM7);
            }

            @Override
            public void visit(String name, Object value) {
                if ("name".equals(name)) {
                    testMethodName = (String) value;
                }
            }

        }

        /**
         * Looking for signatures like:
         * spock/lang/Unroll;(
         * value="test for #a",
         * )
         */
        private static final class SpockUnrollAnnotationVisitor extends AnnotationVisitor {

            private String unrollTemplate;

            public SpockUnrollAnnotationVisitor() {
                super(ASM7);
            }

            @Override
            public void visit(String name, Object value) {
                if ("value".equals(name)) {
                    unrollTemplate = (String) value;
                }
            }

        }

    }

}

