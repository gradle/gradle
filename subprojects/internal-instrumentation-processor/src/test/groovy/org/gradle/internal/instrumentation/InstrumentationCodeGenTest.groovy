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

package org.gradle.internal.instrumentation

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import org.gradle.api.JavaVersion
import org.gradle.internal.instrumentation.processor.ConfigurationCacheInstrumentationProcessor
import org.gradle.internal.jvm.Jvm
import spock.lang.Specification

import javax.tools.JavaFileObject

import static com.google.testing.compile.Compiler.javac

abstract class InstrumentationCodeGenTest extends Specification {

    protected static String fqName(JavaFileObject javaFile) {
        return javaFile.name.replace("/", ".").replace(".java", "");
    }

    protected static JavaFileObject source(String source) {
        def packageGroup = (source =~ "\\s*package ([\\w.]+).*")
        String packageName = packageGroup.size() > 0 ? packageGroup[0][1] : ""
        String className = (source =~ /(?s).*?(?:class|interface|enum) ([\w$]+) .*/)[0][1]
        return packageName.isEmpty()
            ? JavaFileObjects.forSourceString(className, source)
            : JavaFileObjects.forSourceString("$packageName.$className", source);
    }

    protected static Compilation compile(JavaFileObject... fileObjects) {
        return getCompiler()
            .withProcessors(new ConfigurationCacheInstrumentationProcessor())
            .compile(fileObjects)
    }

    private static com.google.testing.compile.Compiler getCompiler() {
        if (Jvm.current().javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
            return javac().withOptions("--release=8")
        }
        return javac()
    }
}
