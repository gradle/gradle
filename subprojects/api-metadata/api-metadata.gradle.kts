import org.gradle.gradlebuild.PublicApi
import org.gradle.gradlebuild.unittestandcompile.ModuleType

plugins {
    gradlebuild.`api-metadata`
}

gradlebuildJava {
    moduleType = ModuleType.INTERNAL
}

apiMetadata {
    includes.addAll(PublicApi.includes)
    excludes.addAll(PublicApi.excludes)
}
