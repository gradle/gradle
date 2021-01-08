plugins {
    id("org.fmiw.plantuml") version "0.1"
}

defaultTasks = listOf("generateDiagrams")

plantuml {
    options {
        outputDir = file("images")
    }
    diagrams {
        for (diagram in listOf("C4_1_Context", "C4_2_Container", "C4_3_Component")) {
            register(diagram) {
                sourceFile = file("$diagram.puml")
            }
        }
    }
}
