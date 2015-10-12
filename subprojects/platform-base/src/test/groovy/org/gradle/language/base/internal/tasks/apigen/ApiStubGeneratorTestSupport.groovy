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

package org.gradle.language.base.internal.tasks.apigen
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.tools.*
import java.lang.reflect.Method

@Requires(TestPrecondition.JDK6_OR_LATER)
class ApiStubGeneratorTestSupport extends Specification {
    private static class JavaSourceFromString extends SimpleJavaFileObject {

        private final String code

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///${ApiStubGeneratorTestSupport.toFileName(name)}"),
                JavaFileObject.Kind.SOURCE)
            this.code = code
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            code
        }
    }

    @CompileStatic
    private static class ApiClassLoader extends URLClassLoader {

        ApiClassLoader() {
            super(new URL[0], systemClassLoader.parent)
        }

        Class<?> loadClassFromBytes(byte[] bytes) {
            defineClass(null, bytes, 0, bytes.length)
        }
    }

    @TupleConstructor
    @CompileStatic
    public static class ApiContainer {
        private final ApiClassLoader apiClassLoader = new ApiClassLoader()
        private final ApiStubGenerator stubgen = new ApiStubGenerator()
        Map<String, GeneratedClass> classes = [:]

        protected Class<?> loadStub(GeneratedClass clazz) {
            apiClassLoader.loadClassFromBytes(stubgen.convertToApi(clazz.bytes))
        }

        protected byte[] getStubBytes(GeneratedClass clazz) {
            stubgen.convertToApi(clazz.bytes)
        }
    }

    @TupleConstructor
    @CompileStatic
    public static class GeneratedClass {
        final byte[] bytes
        final Class<?> clazz
    }

    @CompileStatic
    static String toFileName(String name, boolean clazz=false) {
        "${name.replace('.', '/')}.${clazz?'class':'java'}"
    }

    @Shared
    public JavaCompiler compiler = ToolProvider.systemJavaCompiler

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    protected ApiContainer toApi(String targetVersion = '1.6', Map<String, String> sources) {
        def dir = temporaryFolder.createDir('out')
        def fileManager = compiler.getStandardFileManager(null, null, null)
        def diagnostics = new DiagnosticCollector<JavaFileObject>()
        def task = compiler.getTask(new OutputStreamWriter(
            new ByteArrayOutputStream()),
            fileManager,
            diagnostics,
            ['-d', dir.absolutePath, '-source', targetVersion, '-target', targetVersion],
            [],
            sources.collect { fqn, src -> new JavaSourceFromString(fqn, src) })
        fileManager.close()
        if (task.call()) {
            def classLoader = new URLClassLoader([dir.toURI().toURL()] as URL[], ClassLoader.systemClassLoader.parent)
            // Load the class from the classloader by name....
            return new ApiContainer(sources.collectEntries { name, src ->
                [name, new GeneratedClass(new File(dir, toFileName(name, true)).bytes, classLoader.loadClass(name))]
            })
        }

        StringBuilder sb = new StringBuilder("Error in compilation of test sources:\n")
        diagnostics.diagnostics.each {
            sb.append("In $it\n")
        }


        throw new RuntimeException(sb.toString())
    }

    @CompileStatic
    protected GeneratedClass toClass(String fqn, String script) {
        toApi([(fqn): script]).classes[fqn]
    }

    protected void noSuchMethod(Class c, String name, Class... argTypes) {
        try {
            c.getDeclaredMethod(name, argTypes)
        } catch (NoSuchMethodException ex) {
            return
        }
        throw new AssertionError("Should not have found method $name(${Arrays.toString(argTypes)}) on class $c")
    }

    protected Method hasMethod(Class c, String name, Class... argTypes) {
        try {
            c.getDeclaredMethod(name, argTypes)
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Should have found method $name(${Arrays.toString(argTypes)}) on class $c")
        }
    }

    @Unroll
    def "should create implementation class for #fqn"() {
        given:
        def clazz = toClass(fqn, src).clazz

        expect:
        clazz.name == fqn

        where:
        fqn          | src
        'A'          | 'public class A {}'
        'com.acme.A' | '''package com.acme;

public class A {}
'''
        'com.acme.B' | '''package com.acme;

public class B {
    String getName() { return "foo"; }
}
'''
    }


    def "should compile classes together"() {
        given:
        def api = toApi(
            ['com.acme.A': '''package com.acme;
public class A extends B {}
''',
             'com.acme.B': '''package com.acme;
public class B {
    public String getId() { return "id"; }
}
''']
        )

        when:
        def a = api.classes['com.acme.A'].clazz
        def b = api.classes['com.acme.B'].clazz

        then:
        a.name == 'com.acme.A'
        b.name == 'com.acme.B'

        when:
        def aa = a.newInstance()

        then:
        aa.id == 'id'
    }

}
