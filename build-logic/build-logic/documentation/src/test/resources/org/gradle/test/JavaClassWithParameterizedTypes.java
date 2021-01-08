package org.gradle.test;

import java.util.*;

public class JavaClassWithParameterizedTypes {
    Set<CombinedInterface> getSetProp() { return null; }

    Map<CombinedInterface, JavaClassWithParameterizedTypes> getMapProp() { return null; }

    List<?> getWildcardProp() { return null; }

    List<? extends CombinedInterface> getUpperBoundProp() { return null; }

    List<? super CombinedInterface> getLowerBoundProp() { return null; }

    List<? super Set<? extends Map<?, CombinedInterface[]>>>[] getNestedProp() { return null; }

    <T extends JavaInterface> T paramMethod(T param) {
        return null;
    }
}
