#!/bin/bash
# 1st param is the project in upper case (ex: Lang, Chart, Closure, Math, Time)
# 2nd param is the bug number (ex: 1,2,3,4,...)
# 3rd param is the folder where the genprog project is (ex: /home/mau/Research/genprog4java/ )
# 4td param is the folder where defects4j is installed (ex: /home/mau/Research/defects4j/ )
# 5th param is the option of running it (ex: allHuman, oneHuman, oneGenerated)
# 6th param is the percentage of test cases being used to guide genprog's search (ex: 1, 100)
# 7th param is the folder where the bug files will be cloned to
# 8th param is the initial seed. It will then increase the seeds by adding 1 until it gets to the number in the 9th param.
# 9th param is the final seed.

#cp runGenProgForBug.bash ./genprog4java/defects4jStuff/

#Mau runs it like this:
#./runGenProgForBug.sh Math 2 /home/mau/Research/genprog4java/ /home/mau/Research/defects4j/ allHuman 100 /home/mau/Research/defects4j/ExamplesCheckedOut/ 1 5

#VM:
#./runGenProgForBug.sh Math 2 /home/ubuntu/genprog4java/ /home/ubuntu/defects4j/ allHuman 100 /home/ubuntu/defects4j/ExamplesCheckedOut/ 1 5

if [ "$#" -ne 9 ]; then
    echo "This script should be run with 9 parameters: Project name, bug number, location of genprog4java, defects4j installation, testing option, test suite size, bugs folder, initial seed, final seed"

else

PROJECT="$1"
BUGNUMBER="$2"
GENPROGDIR="$3"
DEFECTS4JDIR="$4"
OPTION="$5"
TESTSUITEPERCENTAGE="$6"
BUGSFOLDER="$7"
STARTSEED="$8"
UNTILSEED="$9"

#This transforms the first parameter to lower case. Ex: lang, chart, closure, math or time
LOWERCASEPACKAGE=`echo $PROJECT | tr '[:upper:]' '[:lower:]'`

#Add the path of defects4j so the defects4j's commands run 
export PATH=$PATH:$DEFECTS4JDIR/framework/bin

# directory with the checked out buggy project
BUGWD=$BUGSFOLDER"/"$LOWERCASEPACKAGE"$BUGNUMBER"Buggy


#Compile Genprog and put the class files in /bin
#Go to the GenProg folder
cd "$GENPROGDIR"
ant -buildfile buildGenProg.xml



#Was trying to make it with javac, ended up doing it with ant 
 #echo Compiling GenProg source files...
 #create file to run compilation
 #FILENAME=sources.txt
 #exec 3<>$FILENAME
 # Write to file
 #echo $LIBSMAIN >&3
 #find -name "*.java" >&3
 #echo $EXTRACLASSES >&3
 #exec 3>&-

 #Compile the project
 #javac @sources.txt
 
 #echo Compilation of main java classes successful
 #rm sources.txt


cd "$GENPROGDIR"/defects4j-scripts/

./prepareBug.sh $PROJECT $BUGNUMBER $GENPROGDIR $DEFECTS4JDIR $OPTION $TESTSUITEPERCENTAGE $BUGSFOLDER






JAVALOCATION=$(which java)

#Go to the working directory
cd $BUGWD/$WD

#CHANGE TO THE WORKING DIRECTORY
#cd $BUGWD/$WD


for (( seed=$STARTSEED; seed<=$UNTILSEED; seed++ ))
#for seed in {0..20..2} #0 to 20, increments of 2
  do	
	echo "RUNNING THE BUG: $PROJECT $BUGNUMBER, WITH THE SEED: $seed"
	
	CHANGESEEDCOMMAND="sed -i '2s/.*/seed = $seed/' "$BUGSFOLDER/"$LOWERCASEPACKAGE""$BUGNUMBER"Buggy/defects4j.config

	eval $CHANGESEEDCOMMAND

	$JAVALOCATION -ea -Dlog4j.configuration=file:"$GENPROGDIR"/src/log4j.properties -Dfile.encoding=UTF-8 -classpath "$GENPROGDIR"/bin:/usr/share/eclipse/dropins/jdt/plugins/org.junit_4.8.2.dist/junit.jar:/usr/share/eclipse/dropins/jdt/plugins/org.hamcrest.core_1.3.0.jar:"$GENPROGDIR"/lib/commons-cli-1.1.jar:"$GENPROGDIR"/lib/commons-collections-3.2.1.jar:"$GENPROGDIR"/lib/commons-exec-1.0.0-SNAPSHOT.jar:"$GENPROGDIR"/lib/commons-io-1.4.jar:"$GENPROGDIR"/lib/jstests.jar:"$GENPROGDIR"/lib/junit-4.10.jar:"$GENPROGDIR"/lib/org.eclipse.core.commands_3.6.0.I20100512-1500.jar:"$GENPROGDIR"/lib/org.eclipse.core.contenttype_3.4.100.v20100505-1235.jar:"$GENPROGDIR"/lib/org.eclipse.core.jobs_3.5.1.R36x_v20100824.jar:"$GENPROGDIR"/lib/org.eclipse.core.resources_3.6.0.R36x_v20100825-0600.jar:"$GENPROGDIR"/lib/org.eclipse.core.runtime_3.6.0.v20100505.jar:"$GENPROGDIR"/lib/org.eclipse.core.runtime.compatibility_3.2.100.v20100505.jar:"$GENPROGDIR"/lib/org.eclipse.equinox.common_3.6.0.v20100503.jar:"$GENPROGDIR"/lib/org.eclipse.equinox.preferences_3.3.0.v20100503.jar:"$GENPROGDIR"/lib/org.eclipse.jdt_3.6.1.v201009090800.jar:"$GENPROGDIR"/lib/org.eclipse.jdt.ui_3.6.1.r361_v20100825-0800.jar:"$GENPROGDIR"/lib/org.eclipse.jface_3.6.1.M20100825-0800.jar:"$GENPROGDIR"/lib/org.eclipse.jface.text_3.6.1.r361_v20100825-0800.jar:"$GENPROGDIR"/lib/org.eclipse.osgi_3.6.1.R36x_v20100806.jar:"$GENPROGDIR"/lib/org.eclipse.osgi.services_3.2.100.v20100503.jar:"$GENPROGDIR"/lib/org.eclipse.osgi.util_3.2.100.v20100503.jar:"$GENPROGDIR"/lib/org.eclipse.text_3.5.0.v20100601-1300.jar:"$GENPROGDIR"/lib/org.eclipse.jdt.core_3.6.1.v_A68_R36x.jar:"$GENPROGDIR"/lib/log4j-1.2.17.jar:"$GENPROGDIR"/lib/org.jacoco.agent-0.7.6.201511241051.jar:"$GENPROGDIR"/lib/org.jacoco.core-0.7.6.201511241051.jar:"$GENPROGDIR"/lib/jacocoagent.jar:"$GENPROGDIR"/lib/asm-all-5.0.4.jar clegoues.genprog4java.main.Main $BUGSFOLDER/"$LOWERCASEPACKAGE""$BUGNUMBER"Buggy/defects4j.config | tee $BUGSFOLDER/"$LOWERCASEPACKAGE""$BUGNUMBER"Buggy/logBug"$BUGNUMBER"Seed$seed.txt


	#Save the variants in a tar file
	tar -cvf $BUGSFOLDER/"$LOWERCASEPACKAGE""$BUGNUMBER"Buggy/variants"$PROJECT""$BUGNUMBER"Seed$seed.tar $BUGSFOLDER/"$LOWERCASEPACKAGE""$BUGNUMBER"Buggy/tmp/

 done

fi #correct number of params
