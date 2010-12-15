package org.gradle.test

class GroovyClassWithParameterizedTypes {
    Set<GroovyInterface> setProp

    Map<GroovyInterface, GroovyClassWithParameterizedTypes> mapProp

    List<?> wildcardProp

    List<? extends GroovyInterface> upperBoundProp

    List<? super GroovyInterface> lowerBoundProp

    List<? super Set<? extends Map<?, GroovyInterface[]>>>[] nestedProp

    static <T> T paramMethod(T param) {
        null
    }
}
