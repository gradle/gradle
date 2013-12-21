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

/**
 * Utility class that allows lists to be reordered.
 */
public class ListReorderer {
    /**
     * Moves the object down one position in the group list.
     *
     * @param sourceList The list whose elements we want to reorder.
     * @param object The object to move.
     */
    public static <T> boolean moveBefore(List<T> sourceList, T object) {
        // Get the old index of the object
        int previousIndex = sourceList.indexOf(object);
        // If the index of the object is 0 it can't go any lower. If it's
        // -1 it's not even in the list. In these cases we do nothing.
        if (previousIndex < 1) {
            return false;
        }
        // Remove the object from it's old position in the list, this shifts all
        // elements above it down by 1
        sourceList.remove(object);
        // Add the object back at 1 less than it's original index, the old
        // element at this position is shifted to right in the lise
        sourceList.add(previousIndex - 1, object);
        // If we get here we assume we moved it.
        return true;
    }

    /**
     * Moves a list of elements in this list while keeping their relative positions. When the first element reaches the beginning it goes no further and the other elements in the list will continue to
     * be shifted on subsequent calls as long as they don't overwrite previously moved elements. This means that elements with other elements between them will continue move with the same distance
     * between them but will 'bunch up' toward the beginning of the list.
     *
     * NOTE: The order of the list of moved elements is important. They have to be added in order from lowest index to highest.
     *
     * @param sourceList The list whose elements we want to reorder.
     * @param objectsToMove List of elements to move.
     */
    public static <T> void moveBefore(List<T> sourceList, List<T> objectsToMove) {
        sortMoveListByRelativeOrder(sourceList, objectsToMove);
        // Create a new list to put elements in we try to move
        List<T> triedToMove = new ArrayList<T>();
        // Now iterate through the elements to move and attempt to move them
        Iterator<T> iterator = objectsToMove.iterator();
        while (iterator.hasNext()) {
            // Get the next object to move
            T objectToMove = iterator.next();
            // Get the index of the object to move
            int currentPosition = sourceList.indexOf(objectToMove);
            // Only move the element if it's not already at the front of the list
            if (currentPosition > 0) {
                // Get the element at the position we want to move to and make sure it's not
                // an element in the list that we'vd already moved
                T occupyingObject = sourceList.get(currentPosition - 1);
                if (currentPosition < sourceList.size() && !triedToMove.contains(occupyingObject)) {
                    moveBefore(sourceList, objectToMove);
                }
            }
            // If we get here we have at least tried to move the object,
            // so stick it in the tried list
            triedToMove.add(objectToMove);
        }
    }

    /**
     * Moves a list of objects to a new index location.
     *
     * @param sourceList The list where the move will occur.
     * @param moveList The objects to move.
     * @param index The object's new location in the list.
     */
    public static <T> void moveTo(List<T> sourceList, List<T> moveList, int index) {
        // First make sure the move is valid
        if (index < 0 || index >= sourceList.size()) {
            return;
        }
        // Store the object at the index to move to
        T moveBeforeObject = sourceList.get(index);

        //This fixes a bug. This happens if the user selects things and moves them to an index where something is already selected. I select 1, 2, and 4 and I say move to 2. 2 is already selected. This makes no sense, but its happened in the field.
        if (moveList.contains(moveBeforeObject)) //just remove the item from the move list.
        {
            List<T> newMoveList = new ArrayList<T>(
                    moveList);   //we don't want to actually affect the move list. Callers use it for visually selecting items after the move. So we'll make a duplicate and just recursively call ourselves again.
            newMoveList.remove(moveBeforeObject);
            moveTo(sourceList, newMoveList, index + 1);   //skip over the one we took out
            return;
        }

        // Remove the object from it's old position
        sourceList.removeAll(moveList);
        // Get the new index value after shifts
        index = sourceList.indexOf(moveBeforeObject);

        //make sure the index is within bounds.
        if (index < 0) {
            index = 0;
        }
        if (index > sourceList.size() - 1) {
            index = sourceList.size() - 1;
        }

        // Add the element to the new location
        sourceList.addAll(index, moveList);
    }

    /**
     * Moves an object to the front of the list.
     *
     * @param sourceList The list the object is in.
     * @param object The object to move.
     * @return True if the object was in the list and it was moved.
     */
    public static <T> boolean moveToFront(List<T> sourceList, T object) {
        boolean moved = false;
        // If we can remove it, then it was in the list
        if (sourceList.remove(object)) {
            sourceList.add(0, object); // This is a void method, so it doesn't set our flag
            moved = true;
        }

        return moved;
    }

    /**
     * Moves a list of objects to the front of the list.
     *
     * @param sourceList The list the object is in.
     * @param objectsToMove The object to move.
     */
    public static <T> void moveToFront(List<T> sourceList, List<T> objectsToMove) {
        sortMoveListByRelativeOrder(sourceList, objectsToMove);
        for (int i = objectsToMove.size() - 1; i >= 0; i--) {
            T object = objectsToMove.get(i);
            if (sourceList.remove(object)) {
                sourceList.add(0, object);
            }
        }
    }

    /**
     * Moves the object up one index position in the list.
     *
     * @param sourceList The list whose elements we want to reorder.
     * @param object The object to move.
     */
    public static <T> boolean moveAfter(List<T> sourceList, T object) {
        // Get the old index of the object
        int previousIndex = sourceList.indexOf(object);
        // If the index of the object is 0 it can't go any higher. If it's
        // -1 it's not even in the list. In these cases we do nothing.
        if (previousIndex >= sourceList.size() - 1 || previousIndex == -1) {
            return false;
        }
        // Remove the object from it's old position in the list, this shifts all
        // elements above it down by 1
        sourceList.remove(object);
        // Add the object back at 2 higher than it's original index, if we only
        // add one than we just place it back where it was since everything shifted
        // down when we removed it
        sourceList.add(previousIndex + 1, object);
        // If we get here we assume we moved it.
        return true;
    }

    /**
     * Moves the objects in the list up one index position in this list while maintaining their relative position. When an element reaches the end of the list it can go no farther, but the other
     * elements continue to move each call without overwriting previously moved elements. This causes moved elements to 'bunch up' at the end of the list.
     *
     * NOTE: The order of the list of moved elements is important. They have to be added in order from lowest index to highest.
     *
     * @param sourceList The list whose elements we want to reorder.
     * @param objectsToMove List of elements to move.
     */
    public static <T> void moveAfter(List<T> sourceList, List<T> objectsToMove) {
        sortMoveListByRelativeOrder(sourceList, objectsToMove);
        List<T> triedToMove = new ArrayList<T>();
        // Since we are moving elements to a greater index in the list,
        // we iterate through the list backwards to move the highest indexed
        // element first
        for (int i = objectsToMove.size() - 1; i >= 0; i--) {
            T objectToMove = objectsToMove.get(i);
            // Get the index of the object to move
            int currentPosition = sourceList.indexOf(objectToMove);
            // Make sure the element we want to move isn't already at the end of
            // the list
            if (currentPosition < sourceList.size() - 1) {
                // Now get the index of the elment occupying the spot we want
                // to move to and only move the current objectToMove if it
                // does not overwite a previously moved element
                T occupyingObject = sourceList.get(currentPosition + 1);
                if (!triedToMove.contains(occupyingObject)) {
                    moveAfter(sourceList, objectToMove);
                }
            }
            // If we get here we have at least tried to move the object,
            // so stick it in the tried list
            triedToMove.add(objectToMove);
        }
    }

    /**
     * Moves an object to the back of the list.
     *
     * @param sourceList The list the object is in.
     * @param object The object to move.
     * @return True if the object was in the list and it was moved.
     */
    public static <T> boolean moveToBack(List<T> sourceList, T object) {
        boolean moved = false;

        // If we can remove it, then it was in the list
        if (sourceList.remove(object)) {
            moved = sourceList.add(object);
        }

        return moved;
    }

    /**
     * Moves a list of objects to the front of the list.
     *
     * @param sourceList The list the object is in.
     * @param objectsToMove The object to move.
     */
    public static <T> void moveToBack(List<T> sourceList, List<T> objectsToMove) {
        sortMoveListByRelativeOrder(sourceList, objectsToMove);
        for (int i = 0; i < objectsToMove.size(); i++) {
            T object = objectsToMove.get(i);
            if (sourceList.remove(object)) {
                sourceList.add(object);
            }
        }
    }

    /**
     * Sorts a child list by position in a parent list to preserve relative ordering of the elements.
     *
     * @param parentList .
     * @param childList .
     */
    public static <T> void sortMoveListByRelativeOrder(final List<T> parentList, List<T> childList) {
        Collections.sort(childList, new Comparator<T>() {
            public int compare(T o, T o1) {
                int index = parentList.indexOf(o);
                int index1 = parentList.indexOf(o1);
                return (index < index1) ? -1 : (index > index1) ? 1 : 0;
            }
        });
    }

    /**
     * Returns true if all the elements of the check list are at the end of the source list.
     *
     * @param sourceList .
     * @param checkList .
     * @return .
     */
    public static <T> boolean allElementsInFront(List<T> sourceList, List<T> checkList) {
        // Quick check, if the source list doesn't contain all elements of the checklist,
        // abort and return false
        if (!sourceList.containsAll(checkList)) {
            return false;
        }
        // Get the last index of the source list
        int sourceIndex = checkList.size();
        // Iterate thru the check list. Find the index of the element
        // in the source list; and check it's index against the index
        // we should be on to match the source.
        for (int index = 0; index < checkList.size(); index++) {
            T element = checkList.get(index);
            int checkIndex = sourceList.indexOf(element);
            if (checkIndex >= sourceIndex) {
                return false;
            }
        }

        return true;
    }

    public static <T> boolean allElementsInBack(List<T> sourceList, List<T> checkList) {
        // Quick check, if the source list doesn't contain all elements of the checklist,
        // abort and return false
        if (!sourceList.containsAll(checkList)) {
            return false;
        }
        // Get the last index of the source list
        int sourceIndex = sourceList.size() - checkList.size();
        // Iterate thru the check list. Find the index of the element
        // in the source list; and check it's index against the index
        // we should be on to match the source.
        for (int index = checkList.size() - 1; index >= 0; index--) {
            T element = checkList.get(index);
            int checkIndex = sourceList.indexOf(element);
            if (checkIndex < sourceIndex) {
                return false;
            }
        }

        return true;
    }

    /**
     * This is mainly used for after doing a move. It gives you the current index of all the moved elements. This is useful for UIs that need to reselect the new items.
     *
     * @param sourceList the source list
     * @param objectsToMove the elements to move
     * @return an integer array of the items to select.
     */
    public static <T> int[] getIndices(List<T> sourceList, List<T> objectsToMove) {
        int[] newIndices = new int[objectsToMove.size()];

        for (int index = 0; index < objectsToMove.size(); index++) {
            T elementToMove = objectsToMove.get(index);
            int sourceIndexOfElement = sourceList.indexOf(elementToMove);

            newIndices[index] = sourceIndexOfElement;
        }

        return newIndices;
    }
}

