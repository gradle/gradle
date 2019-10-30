/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.sample;

public class LinkedList {
    private Node head;

    public void add(String element) {
        Node newNode = new Node(element);

        Node it = tail(head);
        if (it == null) {
            head = newNode;
        } else {
            it.next = newNode;
        }
    }

    private static Node tail(Node head) {
        Node it;

        for (it = head; it != null && it.next != null; it = it.next) {}

        return it;
    }

    public boolean remove(String element) {
        boolean result = false;
        Node previousIt = null;
        Node it = null;
        for (it = head; !result && it != null; previousIt = it, it = it.next) {
            if (0 == element.compareTo(it.data)) {
                result = true;
                unlink(previousIt, it);
                break;
            }
        }

        return result;
    }

    private void unlink(Node previousIt, Node currentIt) {
        if (currentIt == head) {
            head = currentIt.next;
        } else {
            previousIt.next = currentIt.next;
        }
    }

    public int size() {
        int size = 0;

        for (Node it = head; it != null; ++size, it = it.next) {}

        return size;
    }

    public String get(int index) {
        Node it = head;
        while (index > 0 && it != null) {
            it = it.next;
            index--;
        }

        if (it == null) {
            throw new IndexOutOfBoundsException("Index is out of range");
        }

        return it.data;
    }

    private static class Node {
        final String data;
        Node next;

        Node(String data) {
            this.data = data;
        }
    }
}