// tag::apply-plugin[]
plugins {
    `visual-studio`
}
// end::apply-plugin[]

// tag::configure-solution-location[]
visualStudio {
    solution {
        solutionFile.setLocation(file("solution.sln"))
    }
}
// end::configure-solution-location[]

// tag::configure-project-and-filters-location[]
visualStudio {
    projects.all {
        projectFile.setLocation(file("project.vcxproj"))
        filtersFile.setLocation(file("project.vcxproj.filters"))
    }
}
// end::configure-project-and-filters-location[]