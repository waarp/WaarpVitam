#!/bin/sh

java -cp %ROOT%/bin/Waarp-Vitam-Ingest-3.2.0-jar-with-dependencies.jar -Dlogback.configurationFile=%ROOT%/conf/logback.xml org.waarp.openr66.server.ServerInitDatabase %ROOT%/conf/config-serverInitA.xml  -initdb -dir conf -auth %ROOT%/conf/OpenR66-authent-A.xml
