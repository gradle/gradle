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

package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.tooling.BuildAction
import org.gradle.tooling.internal.provider.AbstractClassGraphSpec
import org.gradle.tooling.internal.provider.CustomAction
import org.gradle.tooling.internal.provider.CustomModel
import org.gradle.util.TestClassLoader
import spock.lang.Issue

import java.security.CodeSource
import java.security.Permissions
import java.security.ProtectionDomain
import java.security.cert.Certificate

class ClasspathInfererTest extends AbstractClassGraphSpec {
    def factory = new ClasspathInferer()

    def "determines action and tooling API classpath when loaded via a URLClassLoader"() {
        def cl = urlClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = []
        factory.getClassPathFor(actionClass, classpath)
        def loader = new VisitableURLClassLoader("test", ClassLoader.systemClassLoader.parent, classpath)
        def action = JavaReflectionUtil.newInstance(loader.loadClass(CustomAction.name))
        action.execute(null)
    }

    def "determines action and tooling API classpath when loaded via custom ClassLoader implementation"() {
        def cl = customClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = []
        factory.getClassPathFor(actionClass, classpath)
        def loader = new VisitableURLClassLoader("test", ClassLoader.systemClassLoader.parent, classpath)
        def action = JavaReflectionUtil.newInstance(loader.loadClass(CustomAction.name))
        action.execute(null)
    }

    @Issue("https://github.com/gradle/gradle/issues/1180")
    def "determines action and tooling API classpath when classpath entries contain spaces"() {
        def cl = faultyClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = []
        factory.getClassPathFor(actionClass, classpath)
        def loader = new VisitableURLClassLoader("test", ClassLoader.systemClassLoader.parent, classpath)
        def action = JavaReflectionUtil.newInstance(loader.loadClass(CustomAction.name))
        action.execute(null)
    }

    def "determines action and tooling API classpath when loaded via multiple custom ClassLoader implementations"() {
        def toolingCl = customClassLoader(toolingApiClassPath)
        def cl = customClassLoader(toolingCl, isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = []
        factory.getClassPathFor(actionClass, classpath)
        def loader = new VisitableURLClassLoader("test", ClassLoader.systemClassLoader.parent, classpath)
        def action = JavaReflectionUtil.newInstance(loader.loadClass(CustomAction.name))
        action.execute(null)
    }

    @Issue("GRADLE-3245")
    @LeaksFileHandles
    def "determines action and tooling API classpath when loaded from a jar via a non-standard ClassLoader"() {
        def cl = new NetBeansLikeClassLoader(ClassLoader.systemClassLoader.parent, [isolatedClassesInJar(CustomAction, CustomModel)] + toolingApiClassPath)
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = []
        factory.getClassPathFor(actionClass, classpath)
        def loader
        try {
            loader = new VisitableURLClassLoader("test", ClassLoader.systemClassLoader.parent, classpath)
            def action = JavaReflectionUtil.newInstance(loader.loadClass(CustomAction.name))
            action.execute(null)
        } finally {
            loader?.close()
        }
    }

    private List<File> getToolingApiClassPath() {
        originalClassPath(BuildAction)
    }

    /**
     * A classloader that produces classes with CodeSource objects containing the full resource URL,
     * similar to how the NetBeans JarClassLoader works.
     */
    class NetBeansLikeClassLoader extends TestClassLoader {
        NetBeansLikeClassLoader(ClassLoader classLoader, List<File> classpath) {
            super(classLoader, classpath)
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + '.class'
            URL url = findResource(resource)
            if (url == null) {
                throw new ClassNotFoundException("Could not find class '${name}'")
            }
            def byteCode = url.bytes
            CodeSource codeSource = new CodeSource(getCodeBaseUrl(url, resource), null as Certificate[])
            return defineClass(name, byteCode, 0, byteCode.length, new ProtectionDomain(codeSource, new Permissions(), this, null))
        }

        URL getCodeBaseUrl(URL url, String resource) {
            def uri = url.toURI().toString()
            if (uri.startsWith("jar:")) {
                int pos = uri.indexOf('!')
                def newURI = uri.substring(0, pos + 2)
                return new URI(newURI).toURL()
            } else {
                def newURI = uri - resource
                return new URI(newURI).toURL()
            }
        }
    }
}
