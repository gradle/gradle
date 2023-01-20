/*
 * Copyright 2021 the original author or authors.
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

package com.gradle.internal.compiler.java.listeners

import com.gradle.internal.compiler.java.AbstractCompilerPluginTest
import org.gradle.internal.compiler.java.TestCompiler
import org.gradle.internal.compiler.java.listeners.constants.ConstantDependentsConsumer
import org.gradle.test.fixtures.condition.Requires
import org.gradle.test.fixtures.condition.UnitTestPreconditions

import java.nio.file.Files

class ConstantsCollectorTest extends AbstractCompilerPluginTest {

    TestCompiler compiler
    Map<String, Collection<String>> privateDependentToConstants
    Map<String, Collection<String>> accessibleDependentToConstants

    def setup() {
        privateDependentToConstants = [:]
        accessibleDependentToConstants = [:]
        ConstantDependentsConsumer consumer = new ConstantDependentsConsumer(
            (String constantOrigin, String dependent) -> accessibleDependentToConstants.computeIfAbsent(dependent, { new LinkedHashSet<>() }).add(constantOrigin),
            (String constantOrigin, String dependent) -> privateDependentToConstants.computeIfAbsent(dependent, { new LinkedHashSet<>() }).add(constantOrigin)
        )
        compiler = new TestCompiler(
            Files.createTempDirectory(temporaryFolder.toPath(), null).toFile(),
            { f -> Optional.empty() },
            {},
            {},
            consumer
        )
    }

    def "should not return self as a constant origin class"() {
        given:
        String clazz = """
class A {
    public static final int CONSTANT = 1;
    public static final int CONSTANT2 = A.CONSTANT;

    @Annotation(A.CONSTANT)
    public final int field = A.CONSTANT;
}
"""

        when:
        compiler.compile(toSourceFile(clazz) + getAnnotation("int"))

        then:
        privateDependentToConstants["A"] == null
        accessibleDependentToConstants["A"] == null
    }

    def "collect all constants from annotations on class"() {
        given:
        String clazz = """
import java.util.Map;
@Annotation(Constant1.CONSTANT1 $addition)
class A<@Annotation(Constant2.CONSTANT2 $addition) T> {
    A(@Annotation(Constant3.CONSTANT3 $addition) Map<@Annotation(Constant4.CONSTANT4 $addition) T, String> args) {
        @Annotation(Constant5.CONSTANT5 $addition)
        Map<@Annotation(Constant6.CONSTANT6 $addition) T, String> value = args;
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant4", "Constant5", "Constant6"] as Set
        accessibleDependentToConstants.isEmpty()

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ 1"
        "byte"       | "1"           | "+ 1"
        "String"     | "\"hello\""   | "+ \"world\""
        "int"        | "1"           | "+ 2"
        "boolean"    | "true"        | "& false"
    }

    def "collect all constants from class fields"() {
        given:
        String clazz = """
import java.util.Map;
class A {
    @Annotation(Constant1.CONSTANT1 $addition)
    private Map<@Annotation(Constant2.CONSTANT2 $addition) String, @Annotation(Constant3.CONSTANT3 $addition) String> field;
    static final $constantType STATIC_FINAL_FIELD = Constant4.CONSTANT4 $addition;
    static final $constantType STATIC_FINAL_FIELD_2;
    private final $constantType finalField = Constant5.CONSTANT5 $addition;
    private $constantType fieldDecleration = Constant6.CONSTANT6 $addition;
    static {
        STATIC_FINAL_FIELD_2 = Constant7.CONSTANT7 $addition;
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant5", "Constant6", "Constant7"] as Set
        accessibleDependentToConstants["A"] == ["Constant4"] as Set

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ 1"
        "byte"       | "1"           | "+ 1"
        "String"     | "\"hello\""   | "+ \"world\""
        "int"        | "1"           | "+ 2"
        "boolean"    | "true"        | "& false"
    }

    def "collect all constants from method declaration"() {
        given:
        String clazz = """
import java.util.Map;
class A {
    @Annotation(Constant1.CONSTANT1 $addition)
    private <@Annotation(Constant2.CONSTANT2 $addition) T, @Annotation(Constant3.CONSTANT3 $addition) K>
        Map<@Annotation(Constant4.CONSTANT4 $addition) T, @Annotation(Constant5.CONSTANT5 $addition) K>
            method(Map<@Annotation(Constant6.CONSTANT6 $addition) T, @Annotation(Constant7.CONSTANT7 $addition) K> arg) {
        return null;
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant4", "Constant5", "Constant6", "Constant7"] as Set
        accessibleDependentToConstants.isEmpty()

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ 1"
        "byte"       | "1"           | "+ 1"
        "String"     | "\"hello\""   | "+ \"world\""
        "int"        | "1"           | "+ 2"
        "boolean"    | "true"        | "& false"
    }

    def "collect all constants from method body"() {
        given:
        String clazz = """
class A {
    private void method() {
        @Annotation(Constant1.CONSTANT1 $addition)
        Runnable run = () -> System.out.println(Constant2.CONSTANT2 $addition);
        $constantType value = Constant3.CONSTANT3 $addition;
        if (value == Constant4.CONSTANT4 $addition) {
        }
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant4"] as Set

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ 1"
        "byte"       | "1"           | "+ 1"
        "String"     | "\"hello\""   | "+ \"world\""
        "int"        | "1"           | "+ 2"
        "boolean"    | "true"        | "& false"
    }

    def "collect all from conditional expressions"() {
        given:
        String clazz = """
class A {
    private void method() {
        for ($constantType i = Constant1.CONSTANT1 $addition; i < Constant2.CONSTANT2 $addition; i += Constant3.CONSTANT3 $addition) {
        }
        $constantType value = Constant3.CONSTANT3 $addition;
        switch((int) value) {
            case (int) (Constant4.CONSTANT4 $addition):
            default:
                value = (int) (Constant5.CONSTANT5 $addition);
        }
        while (value < Constant6.CONSTANT6 $addition) {
        }
        do {
        } while (value < Constant7.CONSTANT7 $addition);
        value = value > (Constant8.CONSTANT8 $addition)
                ? ($constantType) (Constant9.CONSTANT9  $addition)
                : ($constantType) (Constant10.CONSTANT10 $addition);
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant4", "Constant5",
                                             "Constant6", "Constant7", "Constant8", "Constant9", "Constant10"] as Set
        accessibleDependentToConstants.isEmpty()

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ (short) 1"
        "byte"       | "1"           | "+ (byte) 1"
        "int"        | "1"           | "+ 2"
    }

    def "collect all static imported constants"() {
        given:
        String clazz = """
import gradle.unit.test.Annotation;
import java.util.Map;
import static gradle.unit.test.Constant1.CONSTANT1;
import static gradle.unit.test.Constant2.CONSTANT2;
import static gradle.unit.test.Constant3.CONSTANT3;
import static gradle.unit.test.Constant4.CONSTANT4;
import static gradle.unit.test.Constant5.CONSTANT5;
import static gradle.unit.test.Constant6.CONSTANT6;
import static gradle.unit.test.Constant7.CONSTANT7;
import static gradle.unit.test.Constant8.CONSTANT8;
import static gradle.unit.test.Constant9.CONSTANT9;
import static gradle.unit.test.Constant10.CONSTANT10;

@Annotation(CONSTANT1 $addition)
class A {
    @Annotation(CONSTANT2 $addition)
    private Map<@Annotation(CONSTANT3 $addition) String, String> field;

    @Annotation(CONSTANT4 $addition)
    private Map<@Annotation(CONSTANT4 $addition) String, String> method(@Annotation(CONSTANT5 $addition) String args) {
        for ($constantType i = CONSTANT5 $addition; i < CONSTANT6 $addition; i += CONSTANT7 $addition) {
        }
        $constantType value = CONSTANT8 $addition;
        switch((int) value) {
            case (int) (CONSTANT9 $addition):
            default:
                value = (int) (CONSTANT10 $addition);
        }
        return null;
    }
}
"""
        List<File> classes = toSourceFile(clazz) \
            + getAnnotation(constantType, "gradle.unit.test") \
            + getConstants(constantType, constantValue, "gradle.unit.test")

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["gradle.unit.test.Constant1", "gradle.unit.test.Constant2",
                                             "gradle.unit.test.Constant3", "gradle.unit.test.Constant4", "gradle.unit.test.Constant5",
                                             "gradle.unit.test.Constant6", "gradle.unit.test.Constant7", "gradle.unit.test.Constant8",
                                             "gradle.unit.test.Constant9", "gradle.unit.test.Constant10"] as Set
        accessibleDependentToConstants.isEmpty()

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ (short) 1"
        "byte"       | "1"           | "+ (byte) 1"
        "int"        | "1"           | "+ 2"
    }

    def "collect all constants from annotation class"() {
        given:
        String clazz = """
@Annotation(value = Constant1.CONSTANT1 $addition)
public @interface A {

    @Annotation(value = Constant2.CONSTANT2 $addition)
    $constantType value() default Constant3.CONSTANT3 $addition;

    $constantType[] values() default { Constant4.CONSTANT4 $addition};

}
"""
        List<File> classes = toSourceFile(clazz) + getAnnotation(constantType) + getConstants(constantType, constantValue)

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1", "Constant2", "Constant3", "Constant4"] as Set
        accessibleDependentToConstants.isEmpty()

        where:
        constantType | constantValue | addition
        "float"      | "0.1f"        | "+ 0.2f"
        "double"     | "0.1"         | "+ 0.2"
        "short"      | "1"           | "+ 1"
        "byte"       | "1"           | "+ 1"
        "String"     | "\"hello\""   | "+ \"world\""
        "int"        | "1"           | "+ 2"
        "boolean"    | "true"        | "& false"
    }

    def "collects constants from accessible static fields as accessible dependents"() {
        given:
        String clazz = """
class A {
    static final int STATIC_FINAL_FIELD = Constant1.CONSTANT1;
}
"""
        List<File> classes = toSourceFile(clazz) + getConstants("int", "1")

        when:
        compiler.compile(classes)

        then:
        accessibleDependentToConstants["A"] == ["Constant1"] as Set
        privateDependentToConstants["A"] == null
    }

    def "collects constants from private static fields as private dependents"() {
        given:
        String clazz = """
class A {
    private static final int STATIC_FINAL_FIELD = Constant1.CONSTANT1;
}
"""
        List<File> classes = toSourceFile(clazz) + getConstants("int", "1")

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1"] as Set
        accessibleDependentToConstants.isEmpty()
    }

    def "collect all constants for chained accessible constants reference"() {
        given:
        List<String> classes = [
            "class A { static final int CONSTANT = Constant1.CONSTANT1; }",
            "class B { static final int CONSTANT = A.CONSTANT; }",
            "class C { static final int CONSTANT = B.CONSTANT; }",
            "class D { static final int CONSTANT = C.CONSTANT; }",
            "class E { }",
        ]
        List<File> sourceFiles = toSourceFiles(classes) + getConstants("int", "1")

        when:
        compiler.compile(sourceFiles)

        then:
        accessibleDependentToConstants["A"] == ["Constant1"] as Set
        accessibleDependentToConstants["B"] == ["A"] as Set
        accessibleDependentToConstants["C"] == ["B"] as Set
        accessibleDependentToConstants["D"] == ["C"] as Set
        accessibleDependentToConstants["E"] == null
        privateDependentToConstants["A"] == null
    }

    def "collect constants from accessible static fields as private dependents when static block is used"() {
        given:
        String clazz = """
class A {
    static final int STATIC_FINAL_FIELD;
    static {
        STATIC_FINAL_FIELD = Constant1.CONSTANT1;
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getConstants("int", "1")

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1"] as Set
        accessibleDependentToConstants.isEmpty()
    }

    def "collect constants from accessible static fields as private dependents on method invocation"() {
        given:
        String clazz = """
class A {
    static final int STATIC_FINAL_FIELD = get(Constant1.CONSTANT1);
    public static int get(int value) {
        return value;
    }
}
"""
        List<File> classes = toSourceFile(clazz) + getConstants("int", "1")

        when:
        compiler.compile(classes)

        then:
        privateDependentToConstants["A"] == ["Constant1"] as Set
        accessibleDependentToConstants.isEmpty()
    }

    def "collect constants from inner classes"() {
        given:
        String clazz = """
class OuterClass {
    void method() {
        Runnable anonymous = new Runnable() {
            @Override
            @Annotation(D.CONSTANT + 1)
            public void run() {
                @Annotation(C.CONSTANT + 1)
                int x = Constant1.CONSTANT1 + 1;
            }
        };
    }
    class A {
        @Annotation(C.CONSTANT + 1)
        private int foo(@Annotation(D.CONSTANT + 1) String arg) { return Constant2.CONSTANT2 + 1; }
    }
    static class B {
        @Annotation(C.CONSTANT + 1)
        private int foo(@Annotation(D.CONSTANT + 1) String arg) { return Constant3.CONSTANT3 + 1; }
    }
    static class C {
        public static final int CONSTANT = 1;
    }
    class D {
        public static final int CONSTANT = 1;
    }
}

"""
        List<File> sourceFiles = toSourceFile(clazz) + getAnnotation("int") + getConstants("int", "1")

        when:
        compiler.compile(sourceFiles)

        then:
        privateDependentToConstants["OuterClass\$1"] == ["Constant1", "OuterClass\$C", "OuterClass\$D"] as Set
        privateDependentToConstants["OuterClass\$A"] == ["Constant2", "OuterClass\$C", "OuterClass\$D"] as Set
        privateDependentToConstants["OuterClass\$B"] == ["Constant3", "OuterClass\$C", "OuterClass\$D"] as Set
        privateDependentToConstants["OuterClass\$C"] == null
        privateDependentToConstants["OuterClass\$D"] == null
        accessibleDependentToConstants.isEmpty()
    }

    def "should collect constants from interface"() {
        given:
        String clazz = """
@Annotation(Constant1.CONSTANT1 + 1)
interface A {
    public static final int CONSTANT = Constant2.CONSTANT2 + 1;
}
"""

        when:
        compiler.compile(toSourceFile(clazz) + getAnnotation("int") + getConstants("int", "1"))

        then:
        privateDependentToConstants["A"] == ["Constant1"] as Set
        accessibleDependentToConstants["A"] == ["Constant2"] as Set
    }

    def "should not collect non-primitive constants"() {
        List<String> classes = [
            "class A { public static final Integer CONSTANT = new Integer(1); }",
            "class B { public static final int CONSTANT = A.CONSTANT; }"
        ]

        when:
        compiler.compile(toSourceFiles(classes))

        then:
        privateDependentToConstants["A"] == null
        privateDependentToConstants["B"] == null
        accessibleDependentToConstants.isEmpty()
    }

    def "inherited constants points to original class"() {
        given:
        String clazz = """
class A {
    static final String STATIC_FINAL_FIELD = "hello";
}
class B extends A {
}
class C {
    static final String STATIC_FINAL_FIELD = B.STATIC_FINAL_FIELD;
}
"""
        List<File> classes = toSourceFile(clazz)

        when:
        compiler.compile(classes)

        then:
        accessibleDependentToConstants["C"] == ["A"] as Set
        privateDependentToConstants["C"] == null
    }

    def "collect all constants for package-info class"() {
        String packageDefinition = """
@PackageInfoAnnotation(Constant.CONSTANT + " world")
package gradle.unit.test;

import gradle.unit.test.PackageInfoAnnotation;

"""
        List<String> classes = ["""
package gradle.unit.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
public @interface PackageInfoAnnotation {
    String value();
}
""",  """
package gradle.unit.test;
public class Constant {
    public static final String CONSTANT = "hello";
}
""",]
        List<File> packageSourceFile = toPackageSourceFile(packageDefinition)
        List<File> classesSourceFiles = toSourceFiles(classes)

        when:
        compiler.compile(classesSourceFiles + packageSourceFile)

        then:
        privateDependentToConstants.isEmpty()
        accessibleDependentToConstants["gradle.unit.test.package-info"] == ["gradle.unit.test.Constant"] as Set
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "collect all constants for module-info class"() {
        String moduleDefinition = """
    import gradle.unit.test.Constant;

    @SuppressWarnings(Constant.CONSTANT)
    module module {
    }
"""
        List<String> classes = ["""
package gradle.unit.test;
public class Constant {
    public static final String CONSTANT = "unchecked";
}
"""]

        when:
        compiler.compile(toModuleSourceFile(moduleDefinition) + toSourceFiles(classes))

        then:
        privateDependentToConstants.isEmpty()
        accessibleDependentToConstants["module-info"] == ["gradle.unit.test.Constant"] as Set
    }

    List<File> getAnnotation(String valueType, String packageName = "") {
        return toSourceFile("""
${packageName ? "package $packageName;" : ""}

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.CONSTRUCTOR,
        ElementType.LOCAL_VARIABLE,
        ElementType.ANNOTATION_TYPE,
        ElementType.PACKAGE,
        ElementType.TYPE_PARAMETER,
        ElementType.TYPE_USE })
@Retention(RetentionPolicy.CLASS)
public @interface Annotation {
    $valueType value();
}
""")
    }

    List<File> getConstants(String constantType, String constantValue, String packageName = "") {
        return toSourceFiles([
            "${packageName ? "package $packageName;" : ""} public class Constant1 { public static final $constantType CONSTANT1 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant2 { public static final $constantType CONSTANT2 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant3 { public static final $constantType CONSTANT3 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant4 { public static final $constantType CONSTANT4 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant5 { public static final $constantType CONSTANT5 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant6 { public static final $constantType CONSTANT6 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant7 { public static final $constantType CONSTANT7 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant8 { public static final $constantType CONSTANT8 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant9 { public static final $constantType CONSTANT9 = $constantValue; }",
            "${packageName ? "package $packageName;" : ""} public class Constant10 { public static final $constantType CONSTANT10 = $constantValue; }"
        ])
    }

}
