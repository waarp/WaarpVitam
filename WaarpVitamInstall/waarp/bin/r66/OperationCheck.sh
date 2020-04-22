#!/bin/sh
if [ $# -eq 0 ]
then
  echo $0 Need at least file argument as /waarp/data/r66/XxxFactory/XxxRequest.xxx.json
  exit 1
fi

java -cp %ROOT%/lib/r66/%WaarpVitam%:%VITAM% -Dlogback.configurationFile=%ROOT%/conf/r66/logback-client.xml -Dvitam.tmp.folder=%ROOT%/tmp/r66 -Dvitam.config.folder=%ROOT%/conf/r66/vitam -Dvitam.data.folder=%ROOT%/data/r66 -Dvitam.log.folder=%ROOT%/log/r66 org.waarp.vitam.common.OperationCheck $1
