/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider

import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.tooling.BuildAction

class ActionClasspathFactoryTest extends AbstractClassGraphSpec {
    def factory = new ActionClasspathFactory()

    def "determines action and tooling API classpath when loaded via a URLClassLoader"() {
        def cl = urlClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction([actionClass])
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    def "caches classpath for a given action class"() {
        def cl = customClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction([actionClass])
        factory.getClassPathForAction([actionClass]) == classpath
    }

    def "determines action and tooling API classpath when loaded via custom ClassLoader implementation"() {
        def cl = customClassLoader(toolingApiClassPath + isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction([actionClass])
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    def "determines action and tooling API classpath when loaded via multiple custom ClassLoader implementations"() {
        def toolingCl = customClassLoader(toolingApiClassPath)
        def cl = customClassLoader(toolingCl, isolatedClasses(CustomAction, CustomModel))
        def actionClass = cl.loadClass(CustomAction.name)

        expect:
        def classpath = factory.getClassPathForAction([actionClass])
        def loader = new MutableURLClassLoader(ClassLoader.systemClassLoader.parent, classpath)
        def action = loader.loadClass(CustomAction.name).newInstance()
        action.execute(null)
    }

    private List<File> getToolingApiClassPath() {
        originalClassPath(BuildAction)
    }

}
