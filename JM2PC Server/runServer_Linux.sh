#!/bin/sh
java -Djava.library.path=.:./lib/comm:./lib/jsr-82 -cp .:./lib/JM2PCServer.jar:./lib/JM2PCommand.jar:./lib/comm/commLinux.jar:./lib/jsr-82/bluecove-2.1.0.jar:./lib/jsr-82/bluecove-gpl-2.1.0.jar jm2pc.server.gui.StartServer
