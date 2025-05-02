package org.gradle.sample.list

class LinkedList {
    private Node head

    void add(String element) {
        Node newNode = new Node(element)

        Node it = tail(head)
        if (it == null) {
            head = newNode
        } else {
            it.next = newNode
        }
    }

    private static Node tail(Node head) {
        Node it

        for (it = head; it != null && it.next != null; it = it.next) {}

        return it
    }

    boolean remove(String element) {
        boolean result = false
        Node previousIt = null
        Node it = null
        for (it = head; !result && it != null; it = it.next) {
            if (element <=> it.data == 0) {
                result = true
                unlink(previousIt, it)
                break
            }
            previousIt = it
        }

        return result
    }

    private void unlink(Node previousIt, Node currentIt) {
        if (currentIt == head) {
            head = currentIt.next
        } else {
            previousIt.next = currentIt.next
        }
    }

    int size() {
        int size = 0

        for (Node it = head; it != null; it = it.next) {
            ++size
        }

        return size
    }

    String get(int index) {
        Node it = head
        while (index > 0 && it != null) {
            it = it.next
            index--
        }

        if (it == null) {
            throw new IndexOutOfBoundsException("Index is out of range")
        }

        return it.data
    }

    private static class Node {
        final String data
        Node next

        Node(String data) {
            this.data = data
        }
    }
}
