# Prints the duration of each "Executing Minify" build operation in a build operation trace log
import json, sys
starts, names = {}, {}
for line in open(sys.argv[1]):
    op = json.loads(line)
    if op.get("displayName", "").startswith("Executing Minify:"):
        starts[op["id"]] = op["startTime"]; names[op["id"]] = op["displayName"].rsplit("/", 1)[1]
    elif "endTime" in op and op["id"] in starts:
        print(f"{(op['endTime'] - starts[op['id']]) / 1000:6.1f}s {names[op['id']]}")
