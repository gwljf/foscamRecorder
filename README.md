foscamRecorder
==============

foscamRecorder is a security camera application that captures live data from a Foscam FI8918W wireless/wired network cameras

foscamRecorder also supports setting up file rolling durations and max diskspace usage. For example, foscamRecorder can be configured to split video files into 60 minute chunks and only to use 10GB of disk storage before overwritting the oldest video file.

BUILDING foscamRecorder
-----------------------------------
1.  (TODO: gradle build system is coming) 

DISCOVER camera URL
-----------------------------------
Camera URL is of the format: `http://<ip>:<port>/<stream page>?<params>`
the two http params needed are user (`user`) and password (`pwd`)

Example Camera URL: `http://10.0.0.132/videostream.cgi?user=myuser&pwd=mypassword`

RECORDING with example GUI
-----------------------------------
The `main()` method in `RecorderGUI.java` implements a simple java gui which provides a graphical interface for recording.

The RecorderGUI provides two input boxes, one for `Cam Name` and another for `Cam URL`.

* Cam Name : Used to name the video files on disk
* Cam URL  : Url used to grab the camera feed (See section above 'DISCOVER camera URL')

RECORDING with command line options
-----------------------------------
The `main()` method in `Recorder.java` implement a command line interface for recording.

```
The command line recorder provides the following options:
    usage: java -jar recorder.jar
       -c <arg>   Cam url (eg. http://<ip>:<port>/<stream page>?<params>
       -d <arg>   Duration (in mins) before cycling to new video file (Defaults
                  to 60mins).
       -h         Print this help message.
       -n <arg>   Webcam name. Defaults to 'webcam'.
       -o <arg>   Output dir location (default: './'). File names will default
                  to '<YYYY.MM.DD-mm-ss>-<webcam name>.mp4'.
       -x <arg>   Max disk space (in megabytes [1024kb]) to use before
                  overwritting recordings. Oldest recordings will be overwritten
                  first. Defaults to -1 (unlimited)
```                  
