/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.compile

import com.sun.tools.javac.util.Context
import com.sun.tools.javac.util.JavacMessages
import spock.lang.Specification

import javax.tools.Diagnostic

class JavacDiagnosticNamesTest extends Specification {

    def messages = JavacMessages.instance(new Context())

    def "javac diagnostics are named after the first line of their message template"() {
        expect:
        JavacDiagnosticNames.nameFor(code, Diagnostic.Kind.ERROR, messages) == name

        where:
        code                                   | name
        "compiler.err.cant.resolve.location"   | "Cannot find symbol"
        "compiler.err.missing.ret.stmt"        | "Missing return statement"
        "compiler.err.warnings.and.werror"     | "Warnings found and -Werror specified"
        "compiler.note.unchecked.recompile"    | "Recompile with -Xlint:unchecked for details."
    }

    def "placeholders are collapsed"() {
        expect:
        JavacDiagnosticNames.nameFor(code, Diagnostic.Kind.ERROR, messages) == name

        where:
        code                                   | name
        "compiler.err.expected"                | "... expected"
        "compiler.warn.redundant.cast"         | "Redundant cast to ..."
        "compiler.err.already.defined"         | "... is already defined in ..."
        "compiler.note.unchecked.filename"     | "... uses unchecked or unsafe operations."
        "compiler.warn.has.been.deprecated"    | "... in ... has been deprecated"
    }

    def "annotation processor messages are named by severity"() {
        expect:
        JavacDiagnosticNames.nameFor(code, kind, messages) == name

        where:
        code                            | kind                              | name
        "compiler.err.proc.messager"    | Diagnostic.Kind.ERROR             | "Annotation processor error"
        "compiler.warn.proc.messager"   | Diagnostic.Kind.WARNING           | "Annotation processor warning"
        "compiler.warn.proc.messager"   | Diagnostic.Kind.MANDATORY_WARNING | "Annotation processor warning"
        "compiler.note.proc.messager"   | Diagnostic.Kind.NOTE              | "Annotation processor note"
    }

    def "codes without a message template are used as the name"() {
        expect:
        JavacDiagnosticNames.nameFor("compiler.err.error.prone", Diagnostic.Kind.ERROR, messages) == "compiler.err.error.prone"
    }

    def "a diagnostic without a code is named by severity"() {
        expect:
        JavacDiagnosticNames.nameFor(null, Diagnostic.Kind.WARNING, messages) == "Java compiler warning"
    }
}
