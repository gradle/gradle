package org.gradle.internal.snapshot.impl

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.state.ManagedFactoryRegistry
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

/**
 * Finite object graphs in which a managed type refers to its own type.
 * None of these contain a cycle, so they must be isolated and snapshotted.
 */
@Issue("https://github.com/gradle/gradle/issues/39202")
class FiniteSelfTypedManagedValueTest extends Specification {

    def classLoaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    def managedFactoryRegistry = Mock(ManagedFactoryRegistry)
    def snapshotter = new DefaultValueSnapshotter([], classLoaderHasher)
    def isolatableFactory = new DefaultIsolatableFactory(classLoaderHasher, managedFactoryRegistry)

    interface Person {
        String getName()

        void setName(String name)

        Person getParent()

        void setParent(Person parent)
    }

    interface LinkedNode {
        Property<String> getName()

        Property<LinkedNode> getNext()
    }

    interface TreeNode {
        Property<String> getName()

        ListProperty<TreeNode> getChildren()
    }

    interface Department {
        ListProperty<Employee> getEmployees()
    }

    interface Employee {
        Property<Department> getSecondedTo()
    }

    private static Person person(String name, Person parent = null) {
        def person = TestUtil.objectFactory().newInstance(Person)
        person.name = name
        person.parent = parent
        return person
    }

    private static LinkedNode linkedNode(String name, LinkedNode next = null) {
        def node = TestUtil.objectFactory().newInstance(LinkedNode)
        node.name.set(name)
        if (next != null) {
            node.next.set(next)
        }
        return node
    }

    private static TreeNode treeNode(String name, TreeNode... children) {
        def node = TestUtil.objectFactory().newInstance(TreeNode)
        node.name.set(name)
        node.children.set(children.toList())
        return node
    }

    private static Department departmentWithSecondedEmployee() {
        def objects = TestUtil.objectFactory()
        def other = objects.newInstance(Department)
        def employee = objects.newInstance(Employee)
        employee.secondedTo.set(other)
        def department = objects.newInstance(Department)
        department.employees.add(employee)
        return department
    }

    def "can isolate a finite chain linked through a mutable property of the same type"() {
        when:
        def isolated = isolatableFactory.isolate(person("child", person("parent")))

        then:
        isolated instanceof IsolatedManagedValue
    }

    def "can snapshot a finite chain linked through a mutable property of the same type"() {
        expect:
        snapshotter.snapshot(person("child", person("parent"))) == snapshotter.snapshot(person("child", person("parent")))
        snapshotter.snapshot(person("child", person("parent"))) != snapshotter.snapshot(person("child", person("other")))
    }

    def "can isolate a finite chain linked through a Property of the same type"() {
        when:
        def isolated = isolatableFactory.isolate(linkedNode("head", linkedNode("tail")))

        then:
        isolated instanceof IsolatedManagedValue
    }

    def "can snapshot a finite chain linked through a Property of the same type"() {
        expect:
        snapshotter.snapshot(linkedNode("head", linkedNode("tail"))) == snapshotter.snapshot(linkedNode("head", linkedNode("tail")))
    }

    def "can isolate a finite tree nested through a ListProperty of the same type"() {
        when:
        def isolated = isolatableFactory.isolate(treeNode("root", treeNode("left"), treeNode("right")))

        then:
        isolated instanceof IsolatedManagedValue
    }

    def "can snapshot a finite tree nested through a ListProperty of the same type"() {
        expect:
        snapshotter.snapshot(treeNode("root", treeNode("left"))) == snapshotter.snapshot(treeNode("root", treeNode("left")))
    }

    def "can isolate a finite graph in which two types refer to each other"() {
        when:
        def isolated = isolatableFactory.isolate(departmentWithSecondedEmployee())

        then:
        isolated instanceof IsolatedManagedValue
    }
}
