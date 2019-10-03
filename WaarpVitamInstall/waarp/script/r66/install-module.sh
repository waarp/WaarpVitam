#!/bin/sh

if [ $# -eq 0 ]
then
  echo Needs at least the jar path of the Waarp-Vitam module as 1rst argument
  exit 1
fi
if [ ! -r $1 ]
then
  echo Needs a valid jar path of the Waarp-Vitam module as 1rst argument
  exit 2
fi
ROOT="$( cd "$(dirname "$0")" ; pwd -P )"
ROOT="$(dirname ${ROOT})"
ROOT="$(dirname ${ROOT})"
echo ROOT of install is ${ROOT}
for FILE in `fgrep -FlR %ROOT% bin conf data`
do
	echo ${FILE}
	sed -i -e "s#%ROOT%#${ROOT}#g" ${FILE}
done
cp $1 ${ROOT}/lib/r66/
WaarpVitam="$(basename $1)"
for FILE in `fgrep -FlR %WaarpVitam% bin conf data`
do
        echo ${FILE}
        sed -i -e "s#%WaarpVitam%#${WaarpVitam}#g" ${FILE}
done

