import os, shutil, sys, tempfile, subprocess

def compile():
    target = sys.argv[1]
    pwd = sys.argv[0][:-len('compile.sh')]
    if not target.endswith('/'):
        target = target + '/'
    javaFiles = []  # file names starting with top-level package name
    for folderName, _, files in os.walk(target):
        for f in files: 
            if f.endswith('.java'):
                relPath = folderName[len(target):]
                javaFiles.append(relPath + '/' + f)

    tempdir = tempfile.mkdtemp()

    for f in javaFiles:
        d = os.path.join(tempdir, f[:f.rfind('/')])
        if not os.path.isdir(d):
            os.makedirs(d)
        # backup
        abs_origin_fp = os.path.join(pwd, 'src/main/java/', f)
        abs_backup = os.path.join(tempdir, f)
        # print('Copying %s to %s' % (abs_origin_fp, abs_backup))
        shutil.copy(abs_origin_fp, abs_backup)
        # copy mutated files to the src folder
        abs_mutated = os.path.join(pwd, target, f)
        # print('Copying %s to %s' % (abs_mutated, abs_origin_fp))
        shutil.copy(abs_mutated, abs_origin_fp)

    # compile with ant
    p = subprocess.run(['ant', 'clean'], cwd = pwd)
    p = subprocess.run(['ant', 'compile.tests'], cwd = pwd)
    if p.returncode != 0:
        sys.exit(-1)
    abs_classes = pwd + 'target/classes/'
    for folderName, _, files in os.walk(abs_classes):
        for f in files: 
            if f.endswith('.class'):
                d = os.path.join(pwd, target, folderName[len(abs_classes):])
                if not os.path.exists(d):
                    os.makedirs(d)
                abs_cls = os.path.join(folderName, f)
                abs_target_cls = os.path.join(d, f)
                # print('Copying %s to %s' % (abs_cls, abs_target_cls))
                shutil.copy(abs_cls, abs_target_cls)

    # recover
    for f in javaFiles:
        # print('Copying %s to %s' % (abs_backup, abs_origin_fp))
        shutil.copy(abs_backup, abs_origin_fp)

    shutil.rmtree(tempdir)
    

if __name__ == "__main__":
    compile()
