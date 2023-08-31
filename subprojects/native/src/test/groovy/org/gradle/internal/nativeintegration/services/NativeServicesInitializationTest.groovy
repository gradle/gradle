/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.nativeintegration.services

import net.rubygrapefruit.platform.Native
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.Factory
import org.gradle.internal.file.Chmod
import org.gradle.internal.reflect.JavaMethod
import org.gradle.internal.service.ServiceRegistry
import org.slf4j.LoggerFactory
import spock.lang.Specification

import javax.inject.Inject
import java.lang.reflect.Constructor

class NativeServicesInitializationTest extends Specification {

    def "cannot get an instance of NativeServices without initializing first" () {
        // Construct an isolated classloader so we can load a pristine NativeServices class
        // that's guaranteed not to have been initialized before
        URL[] jars = [
            jar(Factory),
            jar(NativeServices),
            jar(ServiceRegistry),
            jar(Native),
            jar(LoggerFactory),
            jar(Chmod),
            jar(TemporaryFileProvider),
            jar(Inject)
        ]
        ClassLoader classLoader = new URLClassLoader(jars, null as ClassLoader)
        Class nativeServicesClass = classLoader.loadClass(NativeServices.getName())
        JavaMethod nativeServicesGetInstance = JavaMethod.ofStatic(nativeServicesClass, nativeServicesClass, "getInstance")

        when:
        nativeServicesGetInstance.invokeStatic()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot get an instance of NativeServices without first calling initialize()."
    }

    def "no public constructors on NativeServices" () {
        Constructor[] constructors = NativeServices.getConstructors()

        expect:
        constructors.size() == 0
    }

    private static URL jar(Class clazz) {
        clazz.getProtectionDomain().getCodeSource().getLocation()
    }
}
