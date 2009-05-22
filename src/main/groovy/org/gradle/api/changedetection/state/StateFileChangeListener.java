package org.gradle.api.changedetection.state;

/**
 * @author Tom Eyckmans
 */
interface StateFileChangeListener {

    void itemChanged(StateFileItem oldItem, StateFileItem newItem);

    void itemDeleted(StateFileItem oldItem);

    void itemCreated(StateFileItem newItem);
}
