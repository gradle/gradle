import os
import re
import sys
import shutil

def camel_to_snake(s):
    return re.sub("([A-Z])", "_\\1", s).lower().lstrip("_")

def main():
    # Walk through all files in the directory that contains the files to copy
    for root, dirs, files in os.walk("userguide"):
        for filename in files:
            if filename.endswith(".adoc"):
                old_name = "userguide" + "/" + filename
                new_name = "foo" + "/" + camel_to_snake(filename)
                print old_name + "---->" + new_name
                shutil.copy(old_name, new_name)

if __name__ == "__main__":
    main()
