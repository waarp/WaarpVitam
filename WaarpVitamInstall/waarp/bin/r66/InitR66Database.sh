#!/bin/sh

java -cp %ROOT%/lib/r66/%WaarpVitam% -Dlogback.configurationFile=%ROOT%/conf/r66/logback.xml org.waarp.openr66.server.ServerInitDatabase %ROOT%/conf/r66/config-serverInitA.xml  -initdb -dir %ROOT%/data/r66/conf -auth %ROOT%/conf/r66/OpenR66-authent-A.xml
