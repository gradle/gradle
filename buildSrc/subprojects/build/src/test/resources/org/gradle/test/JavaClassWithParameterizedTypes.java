package org.gradle.test;

import java.util.*;

public class JavaClassWithParameterizedTypes {
    Set<GroovyInterface> getSetProp() { return null; }

    Map<GroovyInterface, JavaClassWithParameterizedTypes> getMapProp() { return null; }

    List<?> getWildcardProp() { return null; }

    List<? extends GroovyInterface> getUpperBoundProp() { return null; }

    List<? super GroovyInterface> getLowerBoundProp() { return null; }

    List<? super Set<? extends Map<?, GroovyInterface[]>>>[] getNestedProp() { return null; }

    <T extends JavaInterface> T paramMethod(T param) {
        return null;
    }
}
