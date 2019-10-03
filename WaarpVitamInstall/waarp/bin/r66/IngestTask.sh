#!/bin/sh
if [ $# -eq 0 ]
then
  echo $0 Need at least file argument -f filepath
  exit 1
fi

java -cp %ROOT%/lib/r66/%WaarpVitam% -Dlogback.configurationFile=%ROOT%/conf/r66/logback.xml -Dvitam.tmp.folder=%ROOT%/tmp/r66 -Dvitam.config.folder=%ROOT%/conf/r66/vitam -Dvitam.data.folder=%ROOT%/data/r66 -Dvitam.log.folder=%ROOT%/log/r66 org.waarp.vitam.ingest.IngestTask -o %ROOT%/conf/r66/config-ingest.property -w %ROOT%/conf/r66/config-clientSubmitA.xml -k -x DEFAULT_WORKFLOW $@
