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
package org.gradle.foundation.common;

import java.util.*;

/*
   Class to store a list whose elements can be reordered. This list is
   meant to be generic so it can be reused.

   The copy method allows for a 'deep copy' of all the list elements. For the
   copy to work properly, all elements stored in the list must implement the
   Copyable interface.

   Unfortunately, we can't use Java's Object.clone() method because it is
   protected and must be overridden as public to be used. So we can't call
   obj.clone() on an Object instance.
*/

public class ReorderableList<E> implements List<E> {
    /**
     * Internal list to store the real elements.
     */
    protected List<E> elements;

    /*
       Parameterized Constructor. This constructor uses an ArrayList by default.
       Use this constructor if iterating through the elements in order is a
       high priority.
    */

    public ReorderableList() {
        // I chose ArrayList over LinkedList simply because of performance.
        // LinkedLists are supposed to have better performance when you move
        // the elements around, as you can do to this list when it is being
        // edited. But editing will happen only rarely and the list will
        // be used heavily when displaying the elements to the user.
        elements = new ArrayList<E>();
    }

    /*
       Parameterized Constructor. This is an alternative where the list-type
       can be passed in. If reordering the list occurs often and has a higher
       priority than in-order iteration. A LinkedList() can be passed in for
       the elementList parameter.

       CAUTION: When using this constructor you should probably make the call
                like the following to prevent the list from being modified
                outside of the ReorderableList instance:

                List list = ReorderableList(  new LinkedList() );

       @param  elementList The list instance used to store elements.
    */

    public ReorderableList(List<E> elementList) {
        this.elements = elementList;
    }

    /*
       Add a object to this ReorderableList.
       @param  object   The object to add.
    */

    public boolean add(E object) {
        return elements.add(object);
    }

    /*
       Retrieve an object from the ReorderableList.
       @param  index      The position of the element to retrieve.
       @return Object - element in the list.
    */

    public E get(int index) {
        return elements.get(index);
    }

    /*
       Add another list to this ReorderableList.
       @param  list   The object to add.
    */

    public void addAll(List<E> list) {
        elements.addAll(list);
    }

    /*
       Remove a object from the ReorderableList.
       @param  object   The object to remove.
       @return True if the ReorderableList contained the object, false
               otherwise.
    */

    public boolean remove(Object object) {
        return elements.remove(object);
    }

    /*
       Retrieves the index of the element in the ReorderableList.
       @param  object   The object whose index we want.
       @return The index of the object in the list or -1 if the object is
               not contained in the list.
    */

    public int indexOf(Object object) {
        return elements.indexOf(object);
    }

    /*
       Returns the number of elements in this ReorderableList.
       @return The number of elements as an int.
    */

    public int size() {
        return elements.size();
    }

    /*
       Test to see if the ReorderableList has elements or not.
       @return True if there are no elements in the list, false otherwise.
    */

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /*
       Moves the object down one position in the list. If the object reaches the
       beginning of the list it obviously can go no farther so subsequent calls
       will have no effect.
       @param  objectToMove    The object to move.
       @return Returns true if the object was in the list and was moved.
    */

    public boolean moveBefore(E objectToMove) {
        return ListReorderer.moveBefore(elements, objectToMove);
    }

    /*
       Moves a list of elements in this list while keeping their relative positions.
       When the first element reaches the beginning it goes no further and the
       other elements in the list will continue to be shifted on subsequent calls
       as long as they don't overwrite previously moved elements. This means that
       elements with other elements between them will continue move with the same
       distance between them but will 'bunch up' toward the beginning of the
       list.

       NOTE: The order of the list of moved elements is important. They have
             to be added in order from lowest index to highest.

       @param  elementsToMove       List of elements to move.
    */

    public void moveBefore(List<E> elementsToMove) {
        ListReorderer.moveBefore(elements, elementsToMove);
    }

    /*
       Move a list of objects to a new specified location.
       @param  objectsToMove The objects to move.
       @param  newIndex     The new location of the object.
    */

    public void moveTo(List<E> objectsToMove, int newIndex) {
        ListReorderer.moveTo(elements, objectsToMove, newIndex);
    }

    /*
       Moves a single object to the front of the list.
       @param  objectToMove The object to move in the list.
       @return True if the object was moved, false otherwise.
    */

    public boolean moveToFront(E objectToMove) {
        return ListReorderer.moveToFront(elements, objectToMove);
    }

    /*
       Moves a list of objects to the front of the list.
       @param  elementsToMove  The list of objects to move in the list.
    */

    public void moveToFront(List<E> elementsToMove) {
        ListReorderer.moveToFront(elements, elementsToMove);
    }

    /*
       Moves the object up one index position higher in the list. If the object
       reaches the end of the list it obviously can go no farther so subsequent
       calls will have no effect.
       @param  objectToMove    The object to move.
       @return Returns true if the object was in the list and was moved.
    */

    public boolean moveAfter(E objectToMove) {
        return ListReorderer.moveAfter(elements, objectToMove);
    }

    /*
       Moves the objects in the list up one index position in this list while
       maintaining their relative position. When an element reaches the end
       of the list it can go no farther, but the other elements continue to
       move each call without overwriting previously moved elements. This
       causes moved elements to 'bunch up' at the end of the list.

       NOTE: The order of the list of moved elements is important. They have
             to be added in order from lowest index to highest.

       @param  elementsToMove     List of elements to move.
    */

    public void moveAfter(List<E> elementsToMove) {
        ListReorderer.moveAfter(elements, elementsToMove);
    }

    /*
       Moves an object to the back of the list.
       @param  objectToMove The object to move.
       @return Returns true if the object was in the list and was moved.
    */

    public boolean moveToBack(E objectToMove) {
        return ListReorderer.moveToBack(elements, objectToMove);
    }

    /*
       Moves a list of objects to the back of the list.
       @param  elementsToMove The list of objects to move.
    */

    public void moveToBack(List<E> elementsToMove) {
        ListReorderer.moveToBack(elements, elementsToMove);
    }

    /*
       @param  checkList  The list of elements to check against our ordered list.
       @return True if the list of passed in elements are all at the front of the
               list, false otherwise.
    */

    public boolean allElementsInFront(List<E> checkList) {
        return ListReorderer.allElementsInFront(elements, checkList);
    }

    /*
       @param  checkList  The list of elements to check against our ordered list.
       @return True if the list of passed in elements are all at the back of the
               list, false otherwise.
    */

    public boolean allElementsInBack(List<E> checkList) {
        return ListReorderer.allElementsInBack(elements, checkList);
    }

    /*
       Returns an Iterator object to iterate through the elements in the
       ReorderableList.
       @return Iterator of Objects. It's up to the caller to cast the elements
               to the appropriate type.
    */

    public Iterator<E> iterator() {
        return elements.iterator();
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////// List Implementation ///////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Removes all of the elements from this list (optional operation).  This list will be empty after this call returns (unless it throws an exception).
     *
     * @throws UnsupportedOperationException if the <tt>clear</tt> method is not supported by this list.
     */
    public void clear() {
        elements.clear();
    }

    /**
     * Returns an array containing all of the elements in this list in proper sequence.  Obeys the general contract of the <tt>Collection.toArray</tt> method.
     *
     * @return an array containing all of the elements in this list in proper sequence.
     */
    public Object[] toArray() {
        return elements.toArray();
    }

    /**
     * Removes the element at the specified position in this list (optional operation).  Shifts any subsequent elements to the left (subtracts one from their indices).  Returns the element that was
     * removed from the list.
     *
     * @param index the index of the element to removed.
     * @return the element previously at the specified position.
     * @throws UnsupportedOperationException if the <tt>remove</tt> method is not supported by this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &gt;= size()).
     */
    public E remove(int index) {
        return elements.remove(index);
    }

    /**
     * Inserts the specified element at the specified position in this list (optional operation).  Shifts the element currently at that position (if any) and any subsequent elements to the right (adds
     * one to their indices).
     *
     * @param index index at which the specified element is to be inserted.
     * @param element element to be inserted.
     * @throws UnsupportedOperationException if the <tt>add</tt> method is not supported by this list.
     * @throws ClassCastException if the class of the specified element prevents it from being added to this list.
     * @throws NullPointerException if the specified element is null and this list does not support null elements.
     * @throws IllegalArgumentException if some aspect of the specified element prevents it from being added to this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &gt; size()).
     */
    public void add(int index, E element) {
        elements.add(index, element);
    }

    /**
     * Returns the index in this list of the last occurrence of the specified element, or -1 if this list does not contain this element. More formally, returns the highest index <tt>i</tt> such that
     * <tt>(o==null ? get(i)==null : o.equals(get(i)))</tt>, or -1 if there is no such index.
     *
     * @param object element to search for.
     * @return the index in this list of the last occurrence of the specified element, or -1 if this list does not contain this element.
     * @throws ClassCastException if the type of the specified element is incompatible with this list (optional).
     * @throws NullPointerException if the specified element is null and this list does not support null elements (optional).
     */
    public int lastIndexOf(Object object) {
        return elements.lastIndexOf(object);
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element. More formally, returns <tt>true</tt> if and only if this list contains at least one element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param object element whose presence in this list is to be tested.
     * @return <tt>true</tt> if this list contains the specified element.
     * @throws ClassCastException if the type of the specified element is incompatible with this list (optional).
     * @throws NullPointerException if the specified element is null and this list does not support null elements (optional).
     */
    public boolean contains(Object object) {
        return elements.contains(object);
    }

    /**
     * Inserts all of the elements in the specified collection into this list at the specified position (optional operation).  Shifts the element currently at that position (if any) and any subsequent
     * elements to the right (increases their indices).  The new elements will appear in this list in the order that they are returned by the specified collection's iterator.  The behavior of this
     * operation is unspecified if the specified collection is modified while the operation is in progress.  (Note that this will occur if the specified collection is this list, and it's nonempty.)
     *
     * @param index index at which to insert first element from the specified collection.
     * @param c elements to be inserted into this list.
     * @return <tt>true</tt> if this list changed as a result of the call.
     * @throws UnsupportedOperationException if the <tt>addAll</tt> method is not supported by this list.
     * @throws ClassCastException if the class of one of elements of the specified collection prevents it from being added to this list.
     * @throws NullPointerException if the specified collection contains one or more null elements and this list does not support null elements, or if the specified collection is <tt>null</tt>.
     * @throws IllegalArgumentException if some aspect of one of elements of the specified collection prevents it from being added to this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &gt; size()).
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        return elements.addAll(index, c);
    }

    /**
     * Appends all of the elements in the specified collection to the end of this list, in the order that they are returned by the specified collection's iterator (optional operation).  The behavior
     * of this operation is unspecified if the specified collection is modified while the operation is in progress.  (Note that this will occur if the specified collection is this list, and it's
     * nonempty.)
     *
     * @param c collection whose elements are to be added to this list.
     * @return <tt>true</tt> if this list changed as a result of the call.
     * @throws UnsupportedOperationException if the <tt>addAll</tt> method is not supported by this list.
     * @throws ClassCastException if the class of an element in the specified collection prevents it from being added to this list.
     * @throws NullPointerException if the specified collection contains one or more null elements and this list does not support null elements, or if the specified collection is <tt>null</tt>.
     * @throws IllegalArgumentException if some aspect of an element in the specified collection prevents it from being added to this list.
     * @see #add(Object)
     */
    public boolean addAll(Collection<? extends E> c) {
        return elements.addAll(c);
    }

    /**
     * Returns <tt>true</tt> if this list contains all of the elements of the specified collection.
     *
     * @param c collection to be checked for containment in this list.
     * @return <tt>true</tt> if this list contains all of the elements of the specified collection.
     * @throws ClassCastException if the types of one or more elements in the specified collection are incompatible with this list (optional).
     * @throws NullPointerException if the specified collection contains one or more null elements and this list does not support null elements (optional).
     * @throws NullPointerException if the specified collection is <tt>null</tt>.
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
        return elements.containsAll(c);
    }

    /**
     * Removes from this list all the elements that are contained in the specified collection (optional operation).
     *
     * @param c collection that defines which elements will be removed from this list.
     * @return <tt>true</tt> if this list changed as a result of the call.
     * @throws UnsupportedOperationException if the <tt>removeAll</tt> method is not supported by this list.
     * @throws ClassCastException if the types of one or more elements in this list are incompatible with the specified collection (optional).
     * @throws NullPointerException if this list contains one or more null elements and the specified collection does not support null elements (optional).
     * @throws NullPointerException if the specified collection is <tt>null</tt>.
     * @see #remove(Object)
     * @see #contains(Object)
     */
    public boolean removeAll(Collection<?> c) {
        return elements.removeAll(c);
    }

    /**
     * Retains only the elements in this list that are contained in the specified collection (optional operation).  In other words, removes from this list all the elements that are not contained in
     * the specified collection.
     *
     * @param c collection that defines which elements this set will retain.
     * @return <tt>true</tt> if this list changed as a result of the call.
     * @throws UnsupportedOperationException if the <tt>retainAll</tt> method is not supported by this list.
     * @throws ClassCastException if the types of one or more elements in this list are incompatible with the specified collection (optional).
     * @throws NullPointerException if this list contains one or more null elements and the specified collection does not support null elements (optional).
     * @throws NullPointerException if the specified collection is <tt>null</tt>.
     * @see #remove(Object)
     * @see #contains(Object)
     */
    public boolean retainAll(Collection<?> c) {
        return elements.retainAll(c);
    }

    /**
     * Returns a view of the portion of this list between the specified <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, exclusive.  (If <tt>fromIndex</tt> and <tt>toIndex</tt> are equal, the
     * returned list is empty.) The returned list is backed by this list, so non-structural changes in the returned list are reflected in this list, and vice-versa. The returned list supports all of
     * the optional list operations supported by this list.<p> <p/> This method eliminates the need for explicit range operations (of the sort that commonly exist for arrays). Any operation that
     * expects a list can be used as a range operation by passing a subList view instead of a whole list.  For example, the following idiom removes a range of elements from a list:
     * <pre>
     *     list.subList(from, to).clear();
     * </pre>
     * Similar idioms may be constructed for <tt>indexOf</tt> and <tt>lastIndexOf</tt>, and all of the algorithms in the <tt>Collections</tt> class can be applied to a subList.<p> <p/> The semantics
     * of the list returned by this method become undefined if the backing list (i.e., this list) is <i>structurally modified</i> in any way other than via the returned list.  (Structural
     * modifications are those that change the size of this list, or otherwise perturb it in such a fashion that iterations in progress may yield incorrect results.)
     *
     * @param fromIndex low endpoint (inclusive) of the subList.
     * @param toIndex high endpoint (exclusive) of the subList.
     * @return a view of the specified range within this list.
     * @throws IndexOutOfBoundsException for an illegal endpoint index value (fromIndex &lt; 0 || toIndex &gt; size || fromIndex &gt; toIndex).
     */
    public List<E> subList(int fromIndex, int toIndex) {
        return elements.subList(fromIndex, toIndex);
    }

    /**
     * Returns a list iterator of the elements in this list (in proper sequence).
     *
     * @return a list iterator of the elements in this list (in proper sequence).
     */
    public ListIterator<E> listIterator() {
        return elements.listIterator();
    }

    /**
     * Returns a list iterator of the elements in this list (in proper sequence), starting at the specified position in this list.  The specified index indicates the first element that would be
     * returned by an initial call to the <tt>next</tt> method.  An initial call to the <tt>previous</tt> method would return the element with the specified index minus one.
     *
     * @param index index of first element to be returned from the list iterator (by a call to the <tt>next</tt> method).
     * @return a list iterator of the elements in this list (in proper sequence), starting at the specified position in this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &gt; size()).
     */
    public ListIterator<E> listIterator(int index) {
        return elements.listIterator(index);
    }

    /**
     * Replaces the element at the specified position in this list with the specified element (optional operation).
     *
     * @param index index of element to replace.
     * @param element element to be stored at the specified position.
     * @return the element previously at the specified position.
     * @throws UnsupportedOperationException if the <tt>set</tt> method is not supported by this list.
     * @throws ClassCastException if the class of the specified element prevents it from being added to this list.
     * @throws NullPointerException if the specified element is null and this list does not support null elements.
     * @throws IllegalArgumentException if some aspect of the specified element prevents it from being added to this list.
     * @throws IndexOutOfBoundsException if the index is out of range (index &lt; 0 || index &gt;= size()).
     */
    public E set(int index, E element) {
        return elements.set(index, element);
    }

    /**
     * Returns an array containing all of the elements in this list in proper sequence; the runtime type of the returned array is that of the specified array.  Obeys the general contract of the
     * <tt>Collection.toArray(Object[])</tt> method.
     *
     * @param objectArray the array into which the elements of this list are to be stored, if it is big enough; otherwise, a new array of the same runtime type is allocated for this purpose.
     * @return an array containing the elements of this list.
     * @throws ArrayStoreException if the runtime type of the specified array is not a supertype of the runtime type of every element in this list.
     * @throws NullPointerException if the specified array is <tt>null</tt>.
     */
    public <T> T[] toArray(T[] objectArray) {
        return elements.toArray(objectArray);
    }

    /*
    This is mainly used for after doing a move. It gives you the current
    index of all the moved elements. This is useful for UIs that need to
    reselect the new items.

    @param  elementsToMove the elements that were moved
    @return                an integer array of the items to select.
    */

    public int[] getIndices(List<E> elementsToMove) {
        return ListReorderer.getIndices(elements, elementsToMove);
    }
}
