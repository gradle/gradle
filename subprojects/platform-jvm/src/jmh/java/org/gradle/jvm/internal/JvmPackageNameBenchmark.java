/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.jvm.internal;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark                                                              Mode  Cnt        Score       Error  Units
 * JvmPackageNameBenchmark.invalid_package_name_creation                 thrpt   20   864371.691 ± 65456.038  ops/s
 * JvmPackageNameBenchmark.package_name_with_lots_of_fragments           thrpt   20  1124642.130 ± 32982.479  ops/s
 * JvmPackageNameBenchmark.reserved_java_keywords_package_name_creation  thrpt   20   687804.950 ± 16936.767  ops/s
 * JvmPackageNameBenchmark.valid_package_name_creation                   thrpt   20   818511.320 ± 99481.866  ops/s
 **/
@Fork(2)
@Warmup(iterations = 10, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = SECONDS)
public class JvmPackageNameBenchmark {

    // values from `JvmPackageNameTest`
    private static final String[] VALID_PACKAGES = {"", "c", "com", "com.p", "com.example", "com.example.p1", "String.i3.αρετη.MAX_VALUE.isLetterOrDigit", "null_", "true_", "_false", "const_", "_public", "com._private", "int_.example", "$", "a.$.b"};
    private static final String[] INVALID_PACKAGES = {" ", ".", "..", "com.", ".com", "1", "com.1", "1com", "com.example.p-1", "com.example.1p", "com/example/1p", "com.example.p 1", "com..example.p1", "null", "true", "false", "const", "public", "com.private", "_", "a._.b"};

    // https://docs.oracle.com/javase/specs/jls/se12/html/jls-3.html#jls-Identifier
    private static final String[] RESERVED_JAVA_KEYWORDS = {
        // https://docs.oracle.com/javase/specs/jls/se12/html/jls-3.html#jls-Keyword
        "abstract", "continue", "for", "new", "switch", "assert", "default", "if", "package", "synchronized", "boolean", "do", "goto", "private", "this", "break", "double", "implements", "protected", "throw", "byte", "else", "import", "public", "throws", "case", "enum", "instanceof", "return", "transient", "catch", "extends", "int", "short", "try", "char", "final", "interface", "static", "void", "class", "finally", "long", "strictfp", "volatile", "const", "float", "native", "super", "while",
        // https://docs.oracle.com/javase/specs/jls/se12/html/jls-3.html#jls-BooleanLiteral
        "true", "false",
        // https://docs.oracle.com/javase/specs/jls/se12/html/jls-3.html#jls-NullLiteral
        "null"
    };

    @Benchmark
    public void valid_package_name_creation(Blackhole bh) {
        for (String p : VALID_PACKAGES) {
            boolean isValid = JvmPackageName.isValid(p);
            bh.consume(isValid);
        }
    }

    @Benchmark
    public void invalid_package_name_creation(Blackhole bh) {
        for (String p : INVALID_PACKAGES) {
            boolean isValid = JvmPackageName.isValid(p);
            bh.consume(isValid);
        }
    }

    @Benchmark
    public void reserved_java_keywords_package_name_creation(Blackhole bh) {
        for (String p : RESERVED_JAVA_KEYWORDS) {
            boolean isValid = JvmPackageName.isValid(p);
            bh.consume(isValid);
        }
    }

    @Benchmark
    public Object package_name_with_lots_of_fragments() {
        return JvmPackageName.isValid("AaBb.CcDd.EeFf.GgHh.IiJj.KkLl.MmNn.OoPp.QqRr.SsTt.UuVv.WwXx.YyZz.AaBb.CcDd.EeFf.GgHh.IiJj.KkLl.MmNn.OoPp.QqRr.SsTt.UuVv.WwXx.YyZz");
    }
}
