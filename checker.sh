#!/usr/lib/bin

FILENAME=$1
CLASSP=$2
VARNAME=$3
ORIGORNOT=$4
GP4JHOME=$5
#variable is deliberately named differently from GP4J_HOME
JAVA8HOME=$6
#variable is deliberately named differently from JAVA_HOME
DAIKONHOME=$7

#if [ $# -ne 7 ]; then
#	echo "Wrong number of arguments"
#	exit 59294
#else

#daikon setup
DAIKONDIR=$DAIKONHOME
JAVA_HOME=$JAVA8HOME
source $DAIKONDIR/scripts/daikon.bashrc

java -cp .:$CLASSP:$CLASSPATH:$DAIKONHOME/daikon.jar:$JAVA8HOME/jre/lib/rt.jar:$JAVA8HOME/lib/tools.jar:$GP4JHOME/lib/javassist.jar ylyu1.wean.Modify JUnitTestRunner $VARNAME > $VARNAME.log

java -cp .:$CLASSP:$CLASSPATH:$DAIKONHOME/daikon.jar:$JAVA8HOME/jre/lib/rt.jar:$JAVA8HOME/lib/tools.jar:$GP4JHOME/lib/javassist.jar clegoues.genprog4java.fitness.JUnitTestRunner $FILENAME > $VARNAME.tuo

java -cp .:$CLASSP:$GP4JHOME/lib/javassist.jar ylyu1.wean.Aggregator $VARNAME $ORIGORNOT > $VARNAME.pred

rm -r org
#TODO: generalize this rm line...
