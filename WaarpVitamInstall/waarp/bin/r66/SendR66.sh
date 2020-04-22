#!/bin/sh

if [ $# -lt 7 ]
then
  echo $0 Need 7 arguments as partner rule requestId applicationSessionId tenantId filename fileinfo
  echo fileinfo may be multiple file informations
  exit 1
fi
# args:
# 1       2     3     4             5         6       7
# partner rule reqid applSessionId tenantId filename fileinfo (last from monitor)
java -cp %ROOT%/lib/r66/%WaarpVitam%:%VITAM% -Dlogback.configurationFile=%ROOT%/conf/r66/logback-client.xml org.waarp.openr66.client.DirectTransfer %ROOT%/conf/r66/config-serverA.xml -to $1 -rule $2 -file $6 -info "$7 $3 $4 $5"

