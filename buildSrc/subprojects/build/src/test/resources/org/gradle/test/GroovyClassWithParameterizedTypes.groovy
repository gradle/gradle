package org.gradle.test

class GroovyClassWithParameterizedTypes {
    Set<CombinedInterface> setProp

    Map<CombinedInterface, GroovyClassWithParameterizedTypes> mapProp

    List<?> wildcardProp

    List<? extends CombinedInterface> upperBoundProp

    List<? super CombinedInterface> lowerBoundProp

    List<? super Set<? extends Map<?, CombinedInterface[]>>>[] nestedProp

    static <T> T paramMethod(T param) {
        null
    }
}
