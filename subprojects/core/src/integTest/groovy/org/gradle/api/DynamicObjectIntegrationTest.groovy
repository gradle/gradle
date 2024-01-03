/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class DynamicObjectIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        file('settings.gradle') << "rootProject.name = 'test'"
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def canAddDynamicPropertiesToProject() {
        createDirs("child")
        file("settings.gradle").writelns("include 'child'")
        file("build.gradle").writelns(
                "ext.rootProperty = 'root'",
                "ext.sharedProperty = 'ignore me'",
                "ext.property = 'value'",
                "convention.plugins.test = new ConventionBean()",
                "task rootTask",
                "task testTask",
                "class ConventionBean { def getConventionProperty() { 'convention' } }"
        )
        file("child/build.gradle").writelns(
                "ext.childProperty = 'child'",
                "ext.sharedProperty = 'shared'",
                "task testTask {",
                "  doLast { new Reporter().checkProperties(project) }",
                "}",
                "assert 'root' == rootProperty",
                "assert 'root' == property('rootProperty')",
                "assert 'root' == properties.rootProperty",
                "assert 'child' == childProperty",
                "assert 'child' == property('childProperty')",
                "assert 'child' == properties.childProperty",
                "assert 'shared' == sharedProperty",
                "assert 'shared' == property('sharedProperty')",
                "assert 'shared' == properties.sharedProperty",
                "assert 'convention' == conventionProperty",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkProperties(object) {",
                "    assert 'root' == object.rootProperty",
                "    assert 'child' == object.childProperty",
                "    assert 'shared' == object.sharedProperty",
                "    assert 'convention' == object.conventionProperty",
                "    assert 'value' == object.property",
                "    assert ':child:testTask' == object.testTask.path",
                "    try { object.rootTask; fail() } catch (MissingPropertyException e) { }",
                "  }",
                "}"
        )

        expectProjectConventionDeprecationWarnings()
        expectConventionTypeDeprecationWarnings(2)

        expect:
        succeeds("testTask")
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def canAddDynamicMethodsToProject() {
        createDirs("child")
        file("settings.gradle").writelns("include 'child'")
        file("build.gradle").writelns(
                "def rootMethod(p) { 'root' + p }",
                "def sharedMethod(p) { 'ignore me' }",
                "convention.plugins.test = new ConventionBean()",
                "task rootTask",
                "task testTask",
                "class ConventionBean { def conventionMethod(name) { 'convention' + name } }"
        )
        file("child/build.gradle").writelns(
                "def childMethod(p) { 'child' + p }",
                "def sharedMethod(p) { 'shared' + p }",
                "task testTask {",
                "  doLast { new Reporter().checkMethods(project) }",
                "}",
                // Use a separate class, to isolate Project from the script
                "class Reporter {",
                "  def checkMethods(object) {",
                "    assert 'rootMethod' == object.rootMethod('Method')",
                "    assert 'childMethod' == object.childMethod('Method')",
                "    assert 'sharedMethod'== object.sharedMethod('Method')",
                "    assert 'conventionMethod' == object.conventionMethod('Method')",
                "    object.testTask { assert ':child:testTask' == delegate.path }",
                "    try { object.rootTask { }; fail() } catch (MissingMethodException e) { }",
                "  }",
                "}"
        )

        expectProjectConventionDeprecationWarnings()
        expectConventionTypeDeprecationWarnings()

        expect:
        succeeds("testTask")
    }

    def canAddMixinsToProject() {

        buildFile '''
convention.plugins.test = new ConventionBean()

assert conventionProperty == 'convention'
assert conventionMethod('value') == '[value]'

class ConventionBean {
    def getConventionProperty() { 'convention' }
    def conventionMethod(String value) { "[$value]" }
}
'''

        expectProjectConventionDeprecationWarnings()
        expectConventionTypeDeprecationWarnings(2)

        expect:
        succeeds()
    }

    def canAddExtensionsToProject() {

        buildFile '''
extensions.test = new ExtensionBean()

assert test instanceof ExtensionBean
test { it ->
    assert it instanceof ExtensionBean
    assert it == project.test
}
class ExtensionBean {
}
'''

        expect:
        succeeds()
    }

    def canAddPropertiesToProjectUsingGradlePropertiesFile() {
        createDirs("child")
        file("settings.gradle").writelns("include 'child'")
        file("gradle.properties") << '''
global=some value
'''
        buildFile '''
assert 'some value' == global
assert hasProperty('global')
assert 'some value' == property('global')
assert 'some value' == properties.global
assert 'some value' == project.global
assert project.hasProperty('global')
assert 'some value' == project.property('global')
assert 'some value' == project.properties.global
'''
        file("child/gradle.properties") << '''
global=overridden value
'''
        file("child/build.gradle") << '''
assert 'overridden value' == global
'''

        expect:
        succeeds()
    }

    def canAddDynamicPropertiesToCoreDomainObjects() {

        buildFile '''
            class GroovyTask extends DefaultTask { }

            task defaultTask {
                ext.custom = 'value'
            }
            task javaTask(type: Copy) {
                ext.custom = 'value'
            }
            task groovyTask(type: GroovyTask) {
                ext.custom = 'value'
            }
            configurations {
                test {
                    ext.custom = 'value'
                }
            }
            dependencies {
                test('::name:') {
                    ext.custom = 'value';
                }
                test(module('::other')) {
                    ext.custom = 'value';
                }
                test(project(':')) {
                    ext.custom = 'value';
                }
                test(files('src')) {
                    ext.custom = 'value';
                }
            }
            repositories {
                ext.custom = 'repository'
            }
            defaultTask.custom = 'another value'
            javaTask.custom = 'another value'
            groovyTask.custom = 'another value'
            assert !project.hasProperty('custom')
            assert defaultTask.hasProperty('custom')
            assert defaultTask.custom == 'another value'
            assert javaTask.custom == 'another value'
            assert groovyTask.custom == 'another value'
            assert configurations.test.hasProperty('custom')
            assert configurations.test.custom == 'value'
            configurations.test.dependencies.each {
                assert it.hasProperty('custom')
                assert it.custom == 'value'
                assert it.getProperty('custom') == 'value'
            }
            assert repositories.hasProperty('custom')
            assert repositories.custom == 'repository'
            repositories {
                assert custom == 'repository'
            }
'''


        expect:
        succeeds("defaultTask")
    }

    def canAddMixInsToCoreDomainObjects() {

        buildFile '''
            class Extension { def doStuff() { 'method' } }
            class GroovyTask extends DefaultTask { }

            task defaultTask {
                convention.plugins.custom = new Extension()
            }
            task javaTask(type: Copy) {
                convention.plugins.custom = new Extension()
            }
            task groovyTask(type: GroovyTask) {
                convention.plugins.custom = new Extension()
            }
            configurations {
                test {
                    convention.plugins.custom = new Extension()
                }
            }
            dependencies {
                test('::name:') {
                    convention.plugins.custom = new Extension()
                }
                test(module('::other')) {
                    convention.plugins.custom = new Extension()
                }
                test(project(':')) {
                    convention.plugins.custom = new Extension()
                }
                test(files('src')) {
                    convention.plugins.custom = new Extension()
                }
            }
            repositories {
                convention.plugins.custom = new Extension()
            }
            assert defaultTask.doStuff() == 'method'
            assert javaTask.doStuff() == 'method'
            assert groovyTask.doStuff() == 'method'
            assert configurations.test.doStuff() == 'method'
            configurations.test.dependencies.each {
                assert it.doStuff() == 'method'
            }
            assert repositories.doStuff() == 'method'
            repositories {
                assert doStuff() == 'method'
            }
'''

        expectConventionTypeDeprecationWarnings(7)
        expectAbstractTaskConventionDeprecationWarnings(3)

        expect:
        succeeds("defaultTask")
    }

    def canAddExtensionsToCoreDomainObjects() {

        buildFile '''
            class Extension { def doStuff() { 'method' } }
            class GroovyTask extends DefaultTask { }

            task defaultTask {
                extensions.test = new Extension()
            }
            task javaTask(type: Copy) {
                extensions.test = new Extension()
            }
            task groovyTask(type: GroovyTask) {
                extensions.test = new Extension()
            }
            configurations {
                test {
                    extensions.test = new Extension()
                }
            }
            dependencies {
                test('::name:') {
                    extensions.test = new Extension()
                }
                test(module('::other')) {
                    extensions.test = new Extension()
                }
                test(project(':')) {
                    extensions.test = new Extension()
                }
                test(files('src')) {
                    extensions.test = new Extension()
                }
            }
            repositories {
                extensions.test = new Extension()
            }
            assert defaultTask.test instanceof Extension
            assert javaTask.test instanceof Extension
            assert groovyTask.test instanceof Extension
            assert configurations.test.test instanceof Extension
            configurations.test.dependencies.each {
                assert it.test instanceof Extension
            }
            assert repositories.test instanceof Extension
            repositories {
                assert test instanceof Extension
            }
'''


        expect:
        succeeds("defaultTask")
    }

    def mixesDslMethodsIntoCoreDomainObjects() {

        buildFile '''
            class GroovyTask extends DefaultTask {
                @Input
                def String prop
                void doStuff(Action<Task> action) { action.execute(this) }
            }
            tasks.withType(GroovyTask) { conventionMapping.prop = { '[default]' } }
            task test(type: GroovyTask)
            assert test.prop == '[default]'
            test {
                description 'does something'
                prop 'value'
            }
            assert test.description == 'does something'
            assert test.prop == 'value'
            test.doStuff {
                prop = 'new value'
            }
            assert test.prop == 'new value'
'''


        expect:
        succeeds("test")
    }

    def mixesConversionMethodsIntoDecoratedObjects() {
        buildFile '''
            enum Letter { A, B, C }
            class SomeThing {
                Letter letter
                def withLetter(Letter l) {
                    letter = l
                }
                def other(String s) {
                    letter = Letter.valueOf(s.substring(0, 1))
                }
                def other(Letter l) {
                    letter = l
                }
                def other(Letter l, String s) {
                    letter = l
                }
            }
            extensions.add('things', SomeThing)
            things {
                letter = 'a'
                withLetter('b')
            }
            assert things.letter == Letter.B
            things.other('ABC')
            assert things.letter == Letter.A
            things.other(Letter.C)
            assert things.letter == Letter.C
            things.other('A', 'ignore')
            assert things.letter == Letter.A
'''

        expect:
        succeeds()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def canAddExtensionsToDynamicExtensions() {

        buildFile '''
            class Extension {
                String name
                Extension(String name) {
                    this.name = name
                }
            }

            project.extensions.create("l1", Extension, "l1")
            project.l1.extensions.create("l2", Extension, "l2")
            project.l1.l2.extensions.create("l3", Extension, "l3")

            task test {
                doLast {
                    assert project.l1.name == "l1"
                    assert project.l1.l2.name == "l2"
                    assert project.l1.l2.l3.name == "l3"
                }
            }
        '''

        expect:
        succeeds("test")
    }

    def canAddMethodsUsingAPropertyWhoseValueIsAClosure() {
        createDirs("child1", "child2")
        file("settings.gradle").writelns("include 'child1', 'child2'");
        buildFile """
            class Thing {
                def prop1 = { it }
            }
            convention.plugins.thing = new Thing()
            ext.prop2 = { it / 2 }

            assert prop1(12) == 12
            assert prop2(12) == 6
        """
        file("child1/build.gradle") << """
            ext.prop3 = { it * 2 }
            assert prop1(12) == 12
            assert prop2(12) == 6
            assert prop3(12) == 24
        """

        expectProjectConventionDeprecationWarnings()
        expectConventionTypeDeprecationWarnings(2)

        expect:
        succeeds()
    }

    def appliesTypeConversionForClosureParameters() {
        buildFile '''
            enum Letter { A, B, C }
            ext.letter = null
            ext.m = { Letter l -> letter = l }
            m("A")
            assert letter == Letter.A
            m(Letter.B)
            assert letter == Letter.B
'''

        expect:
        succeeds()
    }

    def canInjectMethodsFromParentProject() {
        createDirs("child1", "child2")
        file("settings.gradle").writelns("include 'child1', 'child2'");
        buildFile """
            subprojects {
                ext.useSomeProperty = { project.name }
                ext.useSomeMethod = { file(it) }
            }
        """
        file("child1/build.gradle") << """
            task testTask {
                doLast {
                    assert useSomeProperty() == 'child1'
                    assert useSomeMethod('f') == file('f')
                }
            }
        """

        expect:
        succeeds("testTask")
    }

    def canAddNewPropertiesViaTheAdhocNamespace() {

        buildFile """
            assert !hasProperty("p1")

            ext {
                set "p1", 1
            }

            assert p1 == 1
            assert properties.p1 == 1
            assert ext.p1 == 1
            assert hasProperty("p1")
            assert property("p1") == 1
            assert getProperty("p1") == 1
            assert ext.getProperty("p1") == 1

            p1 = 2
            assert p1 == 2
            assert ext.p1 == 2

            task run {
                doLast { task ->
                    assert !task.hasProperty("p1")

                    ext {
                        set "p1", 1
                    }
                    assert p1 == 1
                    assert task.hasProperty("p1")
                    assert task.property("p1") == 1

                    p1 = 2
                    assert p1 == 2
                    assert ext.p1 == 2
                }
            }
        """

        expect:
        succeeds("run")
    }

    def canCallMethodWithClassArgumentType() {
        buildFile """
interface Transformer {}

class Impl implements Transformer {}

class MyTask extends DefaultTask {
    public void transform(Class<? extends Transformer> c) {
        logger.lifecycle("transform(Class)")
    }

    public void transform(Transformer t) {
        logger.lifecycle("transform(Transformer)")
    }
}

task print(type: MyTask) {
    transform(Impl) // should call transform(Class)
}
        """

        expect:
        succeeds("print")
        outputContains("transform(Class)")
    }

    def failsWhenTryingToCallMethodWithClassValue() {
        buildFile """
interface Transformer {}

class Impl implements Transformer {}

class MyTask extends DefaultTask {
    public void transform(Transformer t) {
        logger.lifecycle("transform(Transformer)")
    }
}

task print(type: MyTask) {
    transform(Impl) // should fail since transform(Class) does not exist
}
        """

        expect:
        fails()
        failure.assertHasLineNumber(13)
        failure.assertHasCause("Could not find method transform() for arguments [class Impl] on task ':print' of type MyTask.")
    }

    def failsWhenGettingUnknownPropertyOnProject() {
        buildFile """
            assert !hasProperty("p1")
            println p1
        """

        expect:
        fails()
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not get unknown property 'p1' for root project 'test' of type ${Project.name}.")
    }

    def failsWhenSettingUnknownPropertyOnProject() {
        buildFile """
            assert !hasProperty("p1")

            p1 = 1
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasCause("Could not set unknown property 'p1' for root project 'test' of type ${Project.name}.")
    }

    def failsWhenInvokingUnknownMethodOnProject() {
        buildFile """
            unknown(12, "things")
        """

        expect:
        fails()
        failure.assertHasLineNumber(2)
        failure.assertHasCause("Could not find method unknown() for arguments [12, things] on root project 'test' of type ${Project.name}.")
    }

    def failsWhenGettingUnknownPropertyOnTask() {
        buildFile """
            task p
            assert !tasks.p.hasProperty("p1")
            println tasks.p.p1
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasCause("Could not get unknown property 'p1' for task ':p' of type ${DefaultTask.name}.")
    }

    def failsWhenGettingUnknownPropertyOnDecoratedObject() {
        buildFile """
            class Thing {
            }
            def thing = objects.newInstance(Thing)
            assert !thing.hasProperty("p1")
            println thing.p1
        """

        expect:
        fails()
        failure.assertHasLineNumber(6)
        failure.assertHasCause("Could not get unknown property 'p1' for object of type Thing.")
    }

    def failsWhenGettingUnknownPropertyOnExtensionObject() {
        buildFile """
            class Thing {
            }
            extensions.add('thing', Thing)
            assert !thing.hasProperty("p1")
            println thing.p1
        """

        expect:
        fails()
        failure.assertHasLineNumber(6)
        failure.assertHasCause("Could not get unknown property 'p1' for extension 'thing' of type Thing.")
    }

    def failsWhenGettingUnknownPropertyOnExtensionObjectWithToStringImplementation() {
        buildFile """
            class Thing {
                String toString() { "<thing>" }
            }
            extensions.add('thing', Thing)
            assert !thing.hasProperty("p1")
            println thing.p1
        """

        expect:
        fails()
        failure.assertHasLineNumber(7)
        failure.assertHasCause("Could not get unknown property 'p1' for <thing> of type Thing.")
    }

    def failsWhenGettingUnknownPropertyOnDecoratedObjectThatIsSubjectOfConfigureClosure() {
        buildFile """
            task p
            tasks.p {
                assert !hasProperty("p1")
                println p1
            }
        """

        expect:
        fails()
        failure.assertHasLineNumber(5)
        failure.assertHasCause("Could not get unknown property 'p1' for task ':p' of type ${DefaultTask.name}.")
    }

    def failsWhenSettingUnknownPropertyOnTask() {
        buildFile """
            task p
            assert !tasks.p.hasProperty("p1")
            tasks.p.p1 = 1
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasCause("Could not set unknown property 'p1' for task ':p' of type ${DefaultTask.name}.")
    }

    def failsWhenSettingUnknownPropertyOnDecoratedObject() {
        buildFile """
            class Thing {
                String toString() { "<thing>" }
            }
            extensions.add('thing', Thing)
            assert !thing.hasProperty("p1")
            thing.p1 = 1
        """

        expect:
        fails()
        failure.assertHasLineNumber(7)
        failure.assertHasCause("Could not set unknown property 'p1' for <thing> of type Thing.")
    }

    def failsWhenSettingUnknownPropertyOnDecoratedObjectWhenSubjectOfConfigureClosure() {
        buildFile """
            task p
            tasks.p {
                assert !hasProperty("p1")
                p1 = 1
            }
        """

        expect:
        fails()
        failure.assertHasLineNumber(5)
        failure.assertHasCause("Could not set unknown property 'p1' for task ':p' of type ${DefaultTask.name}.")
    }

    def failsWhenInvokingUnknownMethodOnDecoratedObject() {
        buildFile """
            task p
            tasks.p.unknown(12, "things")
        """

        expect:
        fails()
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not find method unknown() for arguments [12, things] on task ':p' of type ${DefaultTask.name}.")
    }

    def failsWhenInvokingUnknownMethodOnDecoratedObjectWhenSubjectOfConfigureClosure() {
        buildFile """
            task p
            tasks.p {
                unknown(12, "things")
            }
        """

        expect:
        fails()
        failure.assertHasLineNumber(4)
        failure.assertHasCause("Could not find method unknown() for arguments [12, things] on task ':p' of type ${DefaultTask.name}.")
    }

    def canApplyACategoryToDecoratedObject() {
        buildFile '''
            class SomeCategory {
                static String show(Project p, String val) {
                    "project $val path '$p.path'"
                }
                static String show(Task t, String val) {
                    "task $val path '$t.path'"
                }
            }

            task t

            use(SomeCategory) {
                assert project.show("p1") == "project p1 path ':'"
                assert show("p2") == "project p2 path ':'"
                assert t.show("t1") == "task t1 path ':t'"
                tasks.t {
                    assert show("t2") == "task t2 path ':t'"
                }
            }
        '''

        expect:
        succeeds()
    }

    def canAddMethodsAndPropertiesToMetaClassOfDecoratedObject() {
        buildFile '''
            class SomeTask extends DefaultTask {
            }
            class SomeExtension {
            }

            SomeTask.metaClass.p1 = 12
            SomeTask.metaClass.m1 = { -> "m1" }
            SomeExtension.metaClass.p2 = 10
            SomeExtension.metaClass.m2 = { String p -> p }

            task t(type: SomeTask)
            extensions.add("e", SomeExtension)

            assert t.p1 == 12
            assert t.m1() == "m1"
            assert e.p2 == 10
            assert e.m2("hi") == "hi"
        '''

        expect:
        succeeds()
    }

    @Issue("GRADLE-2163")
    def canDecorateBooleanPrimitiveProperties() {

        buildFile """
            class CustomBean {
                boolean b
            }

            // best way to decorate right now
            extensions.create('bean', CustomBean)

            task run {
                doLast {
                    assert bean.b == false
                    bean.conventionMapping.b = { true }
                    assert bean.b == true
                }
            }
        """


        expect:
        succeeds("run")
    }

    def ignoresDynamicBehaviourOfMixIn() {
        buildFile """
            class DynamicThing {
                def methods = [:]
                def props = [:]

                def methodMissing(String name, args) {
                    methods[name] = args.toList()
                }

                def propertyMissing(String name) {
                    props[name]
                }

                def propertyMissing(String name, value) {
                    props[name] = value
                }
            }

            convention.plugins.test = new DynamicThing()

            props

            convention.plugins.test.m1(1,2,3)
            try {
                m1(1,2,3)
                fail()
            } catch (MissingMethodException e) {
                assert e.message == "Could not find method m1() for arguments [1, 2, 3] on root project 'test' of type \${Project.name}."
            }

            convention.plugins.test.p1 = 1
            try {
                p1 = 2
                fail()
            } catch (MissingPropertyException e) {
                assert e.message == "Could not set unknown property 'p1' for root project 'test' of type \${Project.name}."
            }

            convention.plugins.test.p1 += 1
            try {
                p1 += 1
                fail()
            } catch (MissingPropertyException e) {
                assert e.message == "Could not get unknown property 'p1' for root project 'test' of type \${Project.name}."
            }
        """

        expectProjectConventionDeprecationWarnings(4)
        expectConventionTypeDeprecationWarnings(4)

        expect:
        succeeds()
    }

    def canHaveDynamicDecoratedObject() {
        buildFile """
            class DynamicTask extends DefaultTask {
                def methods = [:]
                def props = [:]

                def methodMissing(String name, args) {
                    methods[name] = args.toList()
                }

                def propertyMissing(String name) {
                    props[name]
                }

                def propertyMissing(String name, value) {
                    props[name] = value
                }
            }

            task t(type: DynamicTask)
            t.props
            t.m1(1,2,3)
            t.p1 = 1
            t.p1 += 1

            assert t.methods.size() == 1
            assert t.props.p1 == 2

            t {
                props
                m1(1,2,3)
                p1 = 4
                p1 += 1
            }

            assert t.methods.size() == 1
            assert t.props.p1 == 5
        """

        expect:
        succeeds()
    }

    @Issue("GRADLE-2417")
    def canHaveDynamicExtension() {
        buildFile """
            class DynamicThing {
                def methods = [:]
                def props = [:]

                def methodMissing(String name, args) {
                    methods[name] = args.toList()
                }

                def propertyMissing(String name) {
                    props[name]
                }

                def propertyMissing(String name, value) {
                    props[name] = value
                }
            }

            extensions.create("dynamic", DynamicThing)

            dynamic {
                m1(1,2,3)
                p1 = 1
                p1 += 1
            }

            assert dynamic.methods.size() == 1
            assert dynamic.props.p1 == 2

            dynamic.m1(1,2,3)
            dynamic.p1 = 5
            dynamic.p1 += 1

            assert dynamic.methods.size() == 1
            assert dynamic.props.p1 == 6
        """

        expect:
        succeeds()
    }

    def dynamicPropertiesOfDecoratedObjectTakePrecedenceOverDecorations() {
        buildFile """
            class DynamicTask extends DefaultTask {
                def props = [:]

                def propertyMissing(String name) {
                    if (props.containsKey(name)) {
                        return props[name]
                    }
                    throw new MissingPropertyException(name, DynamicTask)
                }

                def propertyMissing(String name, value) {
                    props[name] = value
                }
            }

            task t(type: DynamicTask)
            t.ext.p1 = 1
            assert t.p1 == 1
            t.p1 = 12
            assert t.p1 == 12

            assert t.ext.p1 == 1
            assert t.props.p1 == 12

            t {
                p1 = 4
                p1 += 1
            }

            assert t.props.p1 == 5
            assert t.ext.p1 == 1
        """

        expect:
        succeeds()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def findPropertyShouldReturnValueIfFound() {
        buildFile """
            task run {
                doLast {
                    assert project.findProperty('foundProperty') == 'foundValue'
                }
            }
        """

        expect:
        executer.withArguments('-PfoundProperty=foundValue')
        succeeds("run")
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def findPropertyShouldReturnNullIfNotFound() {
        buildFile """
            task run {
                doLast {
                    assert project.findProperty('notFoundProperty') == null
                }
            }
        """

        expect:
        succeeds("run")
    }

    private void expectProjectConventionDeprecationWarnings(int repeated = 1) {
        repeated.times {
            executer.expectDocumentedDeprecationWarning(
                "The Project.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
    }

    private void expectConventionTypeDeprecationWarnings(int repeated = 1) {
        repeated.times {
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.Convention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
    }

    private void expectAbstractTaskConventionDeprecationWarnings(int repeated = 1) {
        repeated.times {
            executer.expectDocumentedDeprecationWarning(
                "The AbstractTask.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
    }
}
