.
 | map(select(.unitTests) | .name)
 | to_entries
 | group_by(.key % 10)
 | map({
     name: map(.value) | join(", "),
     tasks: map(.value + ":test") | join(" "),
 })
