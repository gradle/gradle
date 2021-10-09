to_entries | map(.key as $index | .value | has("classes") as $split | {
  filter: $split,
  name: (if $split then
     .subproject + "_" + (.number | tostring)
    else
     .subprojects | join(", ")
    end),
  index: $index,
  tasks: (if $split then
      .subproject + ":quickTest"
    else
      .subprojects | map(. + ":quickTest") | join(" ")
    end
  ),
  filename: (if $split then
      if .include then "include-test-classes" else "exclude-test-classes" end
    else
      ""
    end)
})
