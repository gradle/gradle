/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.internal.classloader

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification

class MultiParentClassLoaderTest extends Specification {
    private ClassLoader parent1 = Mock()
    private ClassLoader parent2 = Mock()
    private MultiParentClassLoader loader = new MultiParentClassLoader(parent1, parent2)

    public void parentsAreNotVisibleViaSuperClass() {
        expect:
        loader.parent == null
    }

    public void loadsClassFromParentsInOrderSpecified() {
        given:
        _ * parent1.loadClass('string') >> String
        _ * parent1.loadClass('integer') >> { throw new ClassNotFoundException() }
        _ * parent2.loadClass('integer') >> Integer

        expect:
        loader.loadClass('string') == String
        loader.loadClass('string', true) == String
        loader.loadClass('integer') == Integer
        loader.loadClass('integer', true) == Integer
    }

    public void throwsCNFExceptionWhenClassNotFound() {
        given:
        _ * parent1.loadClass('string') >> { throw new ClassNotFoundException() }
        _ * parent2.loadClass('string') >> { throw new ClassNotFoundException() }

        when:
        loader.loadClass('string')

        then:
        ClassNotFoundException e = thrown()
        e.message == 'string not found.'
    }

    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    // todo: find a way to mock this in JDK 9+, where `getDefinedPackage` is final
    public void loadsPackageFromParentsInOrderSpecified() {
        def stringPackage = String.class.getPackage()
        def listPackage = List.class.getPackage()

        given:
        _ * parent1.getPackage('string') >> stringPackage
        _ * parent1.getPackage('list') >> null
        _ * parent2.getPackage('list') >> listPackage

        expect:
        loader.getPackage('string') == stringPackage
        loader.getPackage('list') == listPackage
    }

    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    // todo: find a way to mock this in JDK 9+, where `getDefinedPackages` is final
    public void containsUnionOfPackagesFromAllParents() {
        def package1 = Stub(Package)
        def package2 = Stub(Package)
        def package3 = Stub(Package)

        given:
        _ * parent1.getPackages() >> ([package1] as Package[])
        _ * parent2.getPackages() >> ([package1, package2, package3] as Package[])

        expect:
        loader.getPackages() == [package1, package2, package3] as Package[]
    }

    public void loadsResourceFromParentsInOrderSpecified() {
        URL resource1 = new File('res1').toURI().toURL()
        URL resource2 = new File('res2').toURI().toURL()

        given:
        _ * parent1.getResource('resource1') >> resource1
        _ * parent1.getResource('resource2') >> null
        _ * parent2.getResource('resource2') >> resource2

        expect:
        loader.getResource('resource1') == resource1
        loader.getResource('resource2') == resource2
    }

    public void containsUnionOfResourcesFromAllParents() {
        URL resource1 = new File('res1').toURI().toURL()
        URL resource2 = new File('res2').toURI().toURL()
        URL resource3 = new File('res3').toURI().toURL()

        given:
        _ * parent1.getResources('resource') >> { return Collections.enumeration([resource1, resource2]) }
        _ * parent2.getResources('resource') >> { return Collections.enumeration([resource1, resource3]) }

        expect:
        def resources = loader.getResources('resource').collect { it }
        resources == [resource1, resource2, resource3]
    }

    public void visitsSelfAndParents() {
        def visitor = Mock(ClassLoaderVisitor)

        when:
        loader.visit(visitor)

        then:
        1 * visitor.visitSpec({it instanceof MultiParentClassLoader.Spec})
        1 * visitor.visitParent(parent1)
        1 * visitor.visitParent(parent2)
        0 * visitor._
    }

    def "hash code is identity based"() {
        given:
        def c1 = new URLClassLoader()
        def c2 = new CachingClassLoader(c1)
        def loader = new MultiParentClassLoader(c1)
        def hash = loader.hashCode()

        when:
        loader.addParent(c2)

        then:
        loader.hashCode() == hash
    }

    def "equality is identity based"() {
        given:
        def c1 = new URLClassLoader()
        def c2 = new CachingClassLoader(c1)
        def loader1 = new MultiParentClassLoader(c1, c2)
        def loader2 = new MultiParentClassLoader(c1, c2)

        expect:
        loader1 == loader1
        loader2 != loader1
    }

    def "has meaningful toString"() {
        given:
        _ * parent1.toString() >> { "parent1" }
        _ * parent2.toString() >> { "parent2" }

        expect:
        loader.toString() == "MultiParentClassLoader(parent1, parent2)"
    }
}
