import os
import shutil
import subprocess
import sys

import tempfile

"""Used by GenProg to compile Apache Math 

Compilation proceeds in the following steps:
    * Backup original source files
    * Update source code with muated code
    * Invoke ant compile 
    * Recover original source files

To run this script: 
    python3 FULL_PATH_TO_THIS_SCRIPT VARIANT_FOLDER
"""
def compile():
    target = sys.argv[1]
    pwd = sys.argv[0][:-len('compile.sh')]
    if not target.endswith('/'):
        target = target + '/'
    java_files = []  # file names starting with top-level package name
    for folderName, _, files in os.walk(target):
        for f in files: 
            if f.endswith('.java'):
                relPath = folderName[len(target):]
                java_files.append(relPath + '/' + f)

    tempdir = tempfile.mkdtemp()
    backup_overwrite(pwd, target, tempdir, java_files)
    copyVarexCFiles(pwd, target)
    p = subprocess.run(['ant', 'compile.tests'], cwd=pwd)
    # Sanity check and merge check need these new class files in the -classpath argument
    if p.returncode == 0:
        copy_compiled_classes(pwd, target, java_files)
    recover_source_files(pwd, tempdir, java_files)
    cleanupVarexCFiles(pwd)
    shutil.rmtree(tempdir)

    if p.returncode != 0:
        print("Failed to compile %s" % target)
        sys.exit(-1)
    else:
        print("Successfully compiled %s" % target)
        sys.exit(0)

def copyVarexCFiles(pwd, target): 
    source = os.path.join(pwd, target, 'varexc')
    if os.path.exists(source):
        dst = os.path.join(pwd, 'src/java/varexc/')
        shutil.copytree(source, dst)

def cleanupVarexCFiles(pwd): 
    d = os.path.join(pwd, 'src/java/varexc/')
    if os.path.exists(d):
        shutil.rmtree(d)


def recover_source_files(pwd, tempdir, java_files):
    for f in java_files:
        if os.path.exists(os.path.join(tempdir, f)):
            abs_origin_fp = os.path.join(pwd, 'src/java/', f)
            abs_backup = os.path.join(tempdir, f)
            shutil.copy(abs_backup, abs_origin_fp)


def copy_compiled_classes(pwd, target, java_files):
    for f in java_files:
       qualified_class_file = f[:-5] + ".class"
       abs_cls = os.path.join(pwd, 'target/classes', qualified_class_file)
       abs_target_cls = os.path.join(pwd, target, qualified_class_file)
       shutil.copy(abs_cls, abs_target_cls)


"""Backup and overwrite source code with mutated code
"""
def backup_overwrite(pwd, target, tempdir, java_files):
    for f in java_files:
        d = os.path.join(tempdir, f[:f.rfind('/')])
        if not os.path.isdir(d):
            os.makedirs(d)
        # backup, skip if files not exist in the original src folder, e.g., GlobalOptions.java
        abs_origin_fp = os.path.join(pwd, 'src/java/', f)
        if os.path.exists(abs_origin_fp):
            abs_backup = os.path.join(tempdir, f)
            shutil.copy(abs_origin_fp, abs_backup)
        # copy mutated files to the src folder and remove the previously compiled class files
        abs_mutated = os.path.join(pwd, target, f)
        if os.path.exists(abs_origin_fp):
            shutil.copyfile(abs_mutated, abs_origin_fp)
        rm_class_file(pwd, f)


def rm_class_file(pwd, javaFile): 
    classFile = javaFile[:-len(".java")] + ".class"
    abs_classFile = os.path.join(pwd, "target/classes", classFile)
    if os.path.exists(abs_classFile):
        os.remove(abs_classFile)


if __name__ == "__main__":
    compile()
