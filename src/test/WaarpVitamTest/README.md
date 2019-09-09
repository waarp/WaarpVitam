Steps to follow to test or adapt this Waarp plugin with Vitam
=============================================================

## I. Install Vitam

### A. You can follow the instruction for a regular installation from Vitam
directly.

### B. Or use the demo VM for testing given by Vitam 
See https://www.programmevitam.fr/pages/ressources/

For the demo VM from Vitam:
- Follow the instruction from https://www.programmevitam.fr/ressources/DocCourante/autres/fonctionnel/VITAM_Guide_de_prise_en_main.pdf
- Use the examples files from http://download.programmevitam.fr/vitam_repository/2.6.1/tests/Jeu_de_tests_Guide_de_prise_en_main_R10.zip

In particular:
- Choose your tenant (`9` for the example)
- Connect using the default administrator user

      aadmin/aadmin1234
- Upload Rules referential
- Upload Agents services referential
- Upload Access contract
- Upload Ingest contract

In addition, you should allow those access and ingest contracts or all
permissions for this tenant `9`. To do that:
- Disconnect from tenant `9`
- Reconnect to tenant `1` (administration)
- Go to Admin context referential
- Edit the default one `admin-context` `CT-000001`
   - either Add `AC-000001` access contract and `IC-000001`, `IC-000002`, 
   `IC-000003` ingest contracts to tenant `9`
    - either Change `Activation des permissions` to false
- Save the change

## II. Install this directory 

Install this directory into a place where the user Vitam will be allow to
execute, read and write.

For the VM, you can mount a "host" directory into the VM, or you can upload all
files into it.

## III. Configure Waarp Vitam Plugin

### A. For both: initial configuration

Once copied in the correct directory, go to the base directory (for instance:
`/vitam/waarp`) where you placed all subdirs and where this `README.md` and
`install-test.sh` are.

Run `./install-test.sh` which will rewrite all files replacing `%ROOT%` by the
current directory.

Copy the `Waarp-Vitam-1.0.0-jar-with-dependencies.jar` into `%ROOT%/bin/`
directory
(`mvn clean install` to generate this jar into target).

## IV. Adapt according to your need

For Demo Vitam VM, except if Vitam changes its interface, you can keep default
(tenant `9` and default contracts).

### A. For Ingest:

On the Ingest External server (on the demo VM, all services are on the same VM),
ensure the Waarp Server will run using the correct right to write files into
`/vitam/data/ingest-external/upload/`, and that Vitam will be allowed to read and
delete them.
The easiest way is to use Vitam user to run the Waarp Server.

The example of rules for R66 server shows how to use R66 for Vitam. The default
uses the `JAVAEXEC` method, but one can change it to use a `EXEC` command (either in
a script or directly calling the `IngestTask` method). The `EXEC` method should
be used for instance if one should change the properties of the file before
calling the script in order to give access to the incoming file to Vitam
Ingest External service.

    See rulesend-ingest.rule.xml

### B. For DIP:

On any server (on the demo VM, all services are on the same VM), preferably the
Access-External Vitam server for performance reasons.
Again it is reasonable that Waarp uses Vitam user to run the Waarp Server.

The example of rules for R66 server shows how to use R66 for Vitam. The default
uses the `JAVAEXEC` method, but one can change it to use a `EXEC` command (either in
a script or directly calling the `DipTask` method).

    See rulesend-dip.rule.xml

### C. For both: Vitam configuration

Adapt if necessary the Vitam configuration file (ingest and access externals)
placed in `conf/vitam`.
Those comes directly from ihm-demo from demo Vitam VM (`/vitam/conf/ihm-demo`).
They allow to use both services: ingest and access externals.

If you use a regular installation, add the necessary accounts and permission to
allow this Waarp Module to use through REST Vitam API the ingest, access and
 functional administration external services.

The required API are:

1. Ingest
   - POST /ingests
   - GET /ingests/id/type

2. Access
   - POST /dipexport
   - GET /dipexport/id/dip

3. Admin functional
   - HEAD /operations/id

Modify also the `config-ingest.property` and `config-dip.property` accordingly
(tenant and contract for instance).

### D. For both: Monitors configuration

You can specify a different storage for Monitors (Ingest and Dip) than default
(`/tmp/IngestFactory` and `/tmp/DipFactory` respectively) using the java application
option: `-Dorg.waarp.ingest.basedir=/yourDirectory` and `-Dorg.waarp.dip.basedir=/yourDirectory`
respectively.

## V. Initial tests

First, one should test that Vitam is functional, using the IHM Demo from
Vitam. You should be able to ingest a SIP (example: `SIP-Kit_de_prise_en_main.zip`).

You should be able to query Units and get a DIP accordingly (once on a Unit, 
ask for the DIP, then go to the Operation referential and add `rapport
` column from `DIP Export` operation and access to it).

## VI. Waarp Vitam tests

### A. Initialize Waarp R66

You first need to install the database of Waarp:

        ./bin/InitR66Database.sh

Starts Waarp R66 server (using Vitam user preferably):

        ./bin/StartR66ServerVitam.sh

To stop the R66 server, refers to the R66 manual. Shortest way is to start the
 server without background command (no `&`) and to press `CTRL-C` at prompt.
 
### B. Ingest test

Once R66 Server is started on the same host than Ingest External,
you can try to launch the example SIP ingestion.

        ./bin/SendR66.sh SIP-Kit_de_prise_en_main.zip send-ingest

Where you should give the relative path to the SIP at first, then the rule to
 use to send this file to R66. By default, here, `hosta` is used.
 
One can change of course the partner name according to Waarp R66 configuration
 principles. 

Check in the IngestFactory directory (default `/tmp/IngestFactory`) the
 corresponding json file. It should be awaiting for step `RETRY_ATR` (-4).

You can check if the Ingest is done using the following command:

        ./bin/OperationCheck.sh <path_to_json_file>

where path_to_json_file ts something like `/tmp/IngestFactory/IngestRequest.xxxxx.json`.
- This is only a check method, not intended to be used since this is called
 automatically by Waarp-Vitam module.

Now launch the Ingest monitor using:

        ./bin/IngestMonitor.sh
        
It should change the status of the file, retrieve the ATR then deleted the
 json file once done (or error send back).
 
Check in `in/` directory of Waarp to see the file received. Normally, you
 should have received 2 of them: 
 - one for the Request of SIP
 - one for the ATR of this request (Acknowledge Transfer Request)

If any error occurs, you could check the Waarp logs (`log`) or the Vitam logs
 (`/vitam/logs/ingest-external`).
 
If all if ok, retry the same but this time with the Ingest Monitor still on.

To stop the Monitor, you juste have to create the stop file (specified in the
 `bin/IngestMonitor.sh` file as `%ROOT%/conf/ingest_stop.txt`).

### C. DIP test

Once R66 Server is started preferably on the same host than Access External,
you can try to launch the example DIP export.

        ./bin/SendR66.sh data/dsltest.dsl send-dip

Where you should give the relative path to the json containing the DSL Vitam
 file at first, then  the rule to
 use to send this file to R66. By default, here, `hosta` is used.
 
One can change of course the partner name according to Waarp R66 configuration
 principles. 

Check in the DipFactory directory (default `/tmp/DipFactory`) the
 corresponding json file. It should be awaiting for step `RETRY_DIP` (-3).

You can check if the DIP export is done using the following command:

        ./bin/OperationCheck.sh <path_to_json_file>

where path_to_json_file ts something like `/tmp/DipFactory/DipRequest.xxxxx.json`.
- This is only a check method, not intended to be used since this is called
 automatically by Waarp-Vitam module.

Now launch the Dip monitor using:

        ./bin/DipMonitor.sh
        
It should change the status of the file, retrieve the DIP then deleted the
 json file once done (or error send back).
 
Check in `in/` directory of Waarp to see the file received. Normally, you
 should have received 1: 
 - one for the DIP of this request (zip file) or a json error file

If any error occurs, you could check the Waarp logs (`log`) or the Vitam logs
 (`/vitam/logs/access-external`).
 
If all if ok, retry the same but this time with the Dip Monitor still on.

To stop the Monitor, you juste have to create the stop file (specified in the
 `bin/DipMonitor.sh` file as `%ROOT%/conf/dip_stop.txt`).


## VII. Additional informations

### A. IngestTask: Command line help

        usage: IngestTask [-a <arg>] [-c <arg>] [-D <property=value>] -f <arg>
               [-h] [-k] [-n <arg>] [-o <arg>] [-p <arg>] [-r <arg>] [-s <arg>]
               [-t <arg>] [-w <arg>] [-x <arg>]
        Version: Waarp-Vitam.1.0.0
         -a,--access <arg>        (+) Access Contract
         -c,--certificate <arg>   Personal Certificate
         -D <property=value>      Use value for property org.waarp.ingest.basedir
         -f,--file <arg>          (*) Path of the local file
         -h,--help                Get the corresponding help
         -k,--checkatr            If set, after RequestId sent, will check for ATR
                                  if first step is ok
         -n,--action <arg>        Action, shall be always RESUME
         -o,--conf <arg>          (+) Configuration file containing tenant,
                                  access, partner, rule, waarp, certificate
                                  options. Any specific options set independently
                                  will replace the value contained in this file
         -p,--partner <arg>       (+) Waarp Partner
         -r,--rule <arg>          (+) Waarp Rule
         -s,--session <arg>       Application Session Id
         -t,--tenant <arg>        (+) Tenant Id
         -w,--waarp <arg>         (*) Waarp configuration file
         -x,--context <arg>       Context Id, shall be one of DEFAULT_WORKFLOW
                                  (default), HOLDING_SCHEME, FILING_SCHEME
        (*) for mandatory arguments, (+) except if -o config option

### B. IngestMonitor: Command line help

        usage: IngestMonitor [-D <property=value>] [-e <arg>] [-h] -s <arg> -w
               <arg>
        Version: Waarp-Vitam.1.0.0
         -d,--delay <arg>      Delay between 2 retries for pooling in ms greater
                               than 50 (default 100)
         -D <property=value>   Use value for property org.waarp.ingest.basedir
         -e,--elapse <arg>     Elapse time in seconds
         -h,--help             Get the corresponding help
         -r,--retry <arg>      Retry for pooling operation (default 3)
         -s,--stopfile <arg>   (*) Path of the stop file
         -w,--waarp <arg>      (*) Waarp configuration file
        (*) for mandatory arguments

### C. DipTask: Command line help

        usage: DipTask [-a <arg>] [-c <arg>] [-D <property=value>] -f <arg> [-h]
               [-o <arg>] [-p <arg>] [-r <arg>] [-s <arg>] [-t <arg>] [-w <arg>]
        Version: Waarp-Vitam.1.0.0
         -a,--access <arg>        (+) Access Contract
         -c,--certificate <arg>   Personal Certificate
         -D <property=value>      Use value for property org.waarp.dip.basedir
         -f,--file <arg>          (*) Path of the local file
         -h,--help                Get the corresponding help
         -o,--conf <arg>          (+) Configuration file containing tenant,
                                  access, partner, rule, waarp, certificate
                                  options. Any specific options set independently
                                  will replace the value contained in this file
         -p,--partner <arg>       (+) Waarp Partner
         -r,--rule <arg>          (+) Waarp Rule
         -s,--session <arg>       Application Session Id
         -t,--tenant <arg>        (+) Tenant Id
         -w,--waarp <arg>         (*) Waarp configuration file
        (*) for mandatory arguments, (+) except if -o config option

### D. DipMonitor: Command line help

        usage: DipMonitor [-D <property=value>] [-e <arg>] [-h] -s <arg> -w <arg>
        Version: Waarp-Vitam.1.0.0
         -d,--delay <arg>      Delay between 2 retries for pooling in ms greater
                               than 50 (default 100)
         -D <property=value>   Use value for property org.waarp.dip.basedir
         -e,--elapse <arg>     Elapse time in seconds
         -h,--help             Get the corresponding help
         -r,--retry <arg>      Retry for pooling operation (default 3)
         -s,--stopfile <arg>   (*) Path of the stop file
         -w,--waarp <arg>      (*) Waarp configuration file
        (*) for mandatory arguments

### E. OperationCheck: Command line help
        usage: OperationCheck request_json_file_path

