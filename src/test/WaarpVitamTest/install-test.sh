#!/bin/sh

ROOT=`pwd`
for FILE in `fgrep -FlR %ROOT% bin conf`
do
	echo ${FILE}
	sed -i -e "s#%ROOT%#${ROOT}#g" ${FILE}
done

