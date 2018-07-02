#!/bin/sh

STOP_AT_FIRST="yes"
FREE_SEARCH="no"
NB_NODES=1
TIME_LIMIT=900000
JAVA_ARGS=""
CHOCO_JAR=${PARSER_JAR:=~/.m2/repository/org/choco-solver/choco-parsers/4.0.2-SNAPSHOT/choco-parsers-4.0.4-with-dependencies.jar}
usage="\

Usage: fzn_choco.sh [<options>] [<file>]

    Parse and solve <file> using Choco.

OPTIONS:

    -h, --help
        Display this message.

    -a
        This causes the solver to search for, and output all solutions.
        When this option is not given the solver should search for, and output the first solution or the best known one.

    -f
        When invoked with this option the solver ignores any specified search strategy.

    -p
        When invoked with this option the solver is free to use multiple threads and/or cores during search.
        The argument n specifies the number of cores that are available.  (The default is $NB_NODES.)

    -tl <n>
        Limit the resolution time of each problem instance to n ms.  (The default is $TIME_LIMIT.)

    -jar <j>
        Override the jar file.  (The default is JAR.)


    --jargs <args>
		Override default java argument (\"-Xss64m -Xms64m -Xmx4096m -server\")
		
EXAMPLES:
	
	Basic command to solve a fzn model with choco:
	$> sh fzn_exec -jar /path/to/choco-parsers-with-dep.jar ./alpha.fzn

	Additionnal arguments:
	$> sh fzn_exec --jargs \"-Xmx128m\" -tl 100 -jar /path/to/choco-parsers-with-dep.jar ./alpha.fzn

"

if test $# -eq 0
then
    echo "%% No flatzinc file found"
    exit 1
fi

while test $# -gt 0
do
    case "$1" in

        -h|--help)
            echo "$usage"
            exit 0
        ;;

        -a)
            STOP_AT_FIRST="no"
        ;;

        -f)
            FREE_SEARCH="yes"
        ;;

        -p)
            NB_NODES="$2"
            shift
        ;;

        -tl)
            TIME_LIMIT="$2"
            shift
        ;;

        -jar)
            CHOCO_JAR="$2"
            shift
        ;;

    	--jargs)
            JAVA_ARGS="$2"
            shift
        ;;

        -*)
            echo "$0: unknown option \`$1'" 1>&2
            echo "$usage" 1>&2
            exit 2
        ;;

        *)
           break
        ;;

    esac
    shift
done

FILE="$1"
ARGS=" -tl $TIME_LIMIT -p $NB_NODES"

if test "${STOP_AT_FIRST}" = "no"
then
    ARGS=$ARGS" -a"
fi

if test "${FREE_SEARCH}" = "yes"
then
    ARGS=$ARGS" -f"
fi

CMD="java ${JAVA_ARGS} -cp .:${CHOCO_JAR} org.chocosolver.parser.flatzinc.ChocoFZN \"${FILE}\" ${ARGS} -stat"

echo "% $CMD"
eval ${CMD}
