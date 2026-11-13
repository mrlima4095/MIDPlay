   ---=====================================---
        JM2PC - Java Mobile to PC - 3.1.0
   ---=====================================---
             Walter Alves Wanderley


          http://jm2pc.sourceforge.net
               -----------------
                    v3.1.0
                  10/23/2008

==============================================================

Contents:


1. Introduction
2. System Requirements
3. JM2PC Server
4. JM2PC Client
5. JM2PC BT Client
6. JM2PC Registration
7. Plug-ins
8. Contact us


==============================================================

1. Introduction

==============================================================

The JM2PC is software that allows the remote control of computers 
through mobile phones. It's possible to control, mouse, keyboard, 
to have access files, start and stop programs execute, commands, 
send messages, view the desktop screen of in the mobile, etc. 
Some functionality can be inserted to the JM2PC through the 
system of Plugins.

The system has Client-Server architecture. The Client (JM2PC 
Client) must be installed in mobile phone and the Server 
(JM2PC Server) must be installed in a desktop or laptop computer. 
They can connect themselves through the Internet or Bluetooth 
technology. 

This document is only a mini-tutorial to configure and get access 
to JM2PC. A manual covering all functions of the JM2PC and with 
more details on its configuration can be downloaded at 
http://jm2pc.sourceforge.net.


==============================================================

2. System Requirements

==============================================================

# JM2PC Server:
	
	- A desktop or laptop computer with Java* (JRE) version 1.6 or superior
        - Internet connection or Bluetooth adapter

	* If you don't have Java installed on your computer 
          get it at: http://java.sun.com/javase/downloads/index.jsp


# JM2PC Client:

	- A mobile phone that's support Java applications (MIDP 1.0) 
        - GPRS or EDGE
        - at least 70 KB of available storage


# JM2PC BT Client (for bluetooth connections):
	
	- A mobile phone that's support Java applications (MIDP 1.0)
	- Bluetooth and JSR-82
	- at least 80 KB of available storage


==============================================================

3. JM2PC Server

==============================================================

The JM2PC Server is a software that must be installed in the Computer to 
allow the remote control with mobile phone. 


JM2PC Server Installation:
-----------------------------------

The latest version of JM2PC Server can be downloaded at http://jm2pc.sourceforge.net
(download section). Download JM2PC_Server.zip and extract it on your 
hard disk.


Running JM2PC Server:
-----------------------------

- Windows: 

  	Run the file "RunServer_windows.bat"


- Linux:

  	Add execute permission and run "runServer_linux.sh" file.
  	Example:

       		$ cd <JM2PC Server installation path>
       		$ chmod +x runServer_linux.sh
       		$ ./runServer_linux.sh


- Other OS:

	Execute the command line:

  	java -Djava.library.path=.:./lib/comm:./lib/jsr-82 -cp .:./lib/JM2PCServer.jar:./lib/JM2PCommand.jar:./lib/comm/commLinux.jar:./lib/jsr-82/your_JSR82_implementation.jar jm2pc.server.gui.StartServer


JM2PC Server Configuration:
--------------------------------------

- Language: select the language of JM2PC Server's GUI

- Save Log: enable/disable save log to files

- Timeout (min): timeout (in minutes) of JM2PC Client sessions. 
  It can be a value between 0 and 60. A timeout of zero is 
  interpreted as an infinite timeout.

- Password: choose a password to protect your server.


# Internet Mode Configuration:
-----------------------------------------

The following steps will show you how to configure JM2PC Server 
to connect to JM2PC Client through internet:

1 - Select the Internet mode.
2 - Port: The port must be a number between 1 and 65535. 
    if you have any type of Firewall, you need to allow 
    the access to this port.
3 - SSL: Enable/disable Secure Sockets Layer.
4 - Click the Start button. A message indicating 
    the successfully of the operation and the server 
    is waiting for the clients will be shown

Ps: You need a direct connection to the Internet. 
    If you share your internet connection with others 
    computers, you need to do a port forward 
    (www.portforward.com for more information).



# Bluetooth Mode Configuration:
-------------------------------------------

-JSR-82 Mode:
--------------

    JM2PC Server uses the JSR-82 especification to provide bluetooth connection;


1 - In JM2PC Server, select the Bluetooth connection type and JSR-82 mode
2 - Enter a password
3 - Click the Start button. A message will be displayed indicating the 
    successfully of the operation and the server is waiting for the clients.


- COMM Mode:
-------------


1- Bluetooth COMM (Serial Port Profile) configuration:

In the COMM mode JM2PC Server uses the Bluetooth Serial Port Profile 
available in most of Bluetooth stack. Then, you need to 
configure this service.

----------------------------------------------------------------------------------------------

   - Windows

      - BlueSoleil:

        In the BlueSoleil software access menu (My Services -> Properties), 
        select the (Serial Port A) tab, enable the option (Auto-start this 
        service when my Bluetooth starts) and view what's the COM port is 
        used to provide access to this service.

      - Widcomm:

        Open Bluetooth configuration from the Control Panel, select the 
        (Local Service) tab and view what's the COM port is used to 
        provide access to Bluetooth Serial Port. If you don't have a 
        Bluetooth Serial Port service, click the (Add Serial Service) 
        button to create the service.

      - Another Bluetooth stacks:

        Read in the documentation how to enable and the COM port is used 
        for the Bluetooth Serial Port Profile.

   - Linux

      Linux users can use BlueZ (www.blueZ.org).

      # sdptool add --channel=1 SP
      # rfcomm listen /dev/rfcomm0 1

      Is necessary substitute the file javax.comm.properties 
      (in JM2PC Server/lib directory) with LINUX_javax.comm.properties file.

      $ cd < JM2PC Server installation path >/lib
      $ mv LINUX_javax.comm.properties javax.comm.properties


   - Another Operational Systems

      Users of other operational systems (MacOS or Solaris) must install 
      some implementation of javax.comm for its operational system to 
      activate the support to the Serial Port Service in the JM2PC Server.

------------------------------------------------------------------------------------------------------

2 - Backing to the JM2PC Server, select the Bluetooth mode.
3 - Port: Select the COM Port used for the Bluetooth Serial Port Profile 
    discovery on the step 1.
4 - Bps: The baud rate of  the COM Port.
5 - Click the Start button. A message will be displayed indicating the 
    successfully of the operation and the server is waiting for the clients.


------------------------------------------------------------------------------------------------------


1- Bluetooth JSR-82 configuration:

In the JSR-82 mode JM2PC Server uses the Bluetooth API JSR-82.


==============================================================

4. JM2PC Client

==============================================================

The JM2PC Client is a software that must be installed in the mobile phone 
to remote control computers through an internet connection (GPRS, EDGE or 3G).
If you  have a mobile phone with Bluetooth technology, you can install 
JM2PC BT Client for Bluetooth connections to PC.


ATTENTION: YOU MUST INSTALL THE SAME VERSION NUMBER OF JM2PC SERVER 
AND JM2PC CLIENT.


JM2PC Client Installation:
----------------------------------

You can download the latest version of JM2PC Client at http://jm2pc.sourceforge.net 
(files section). Download JM2PC_Client.jar and JM2PC_Client.jad 
and upload the files to your wireless handheld using a USB cable, 
infrared, or bluetooth. 



Running JM2PC Client:
----------------------------

Select JM2PC Client from applications menu on your mobile phone.


JM2PC Client Configuration:
-------------------------------------

To start a connection to a JM2PC Server, you need to add a server 
profile in JM2PC Client. 

1 - Select (options -> New)
2 - Select the connection type - Internet (GPRS/EDGE/3G)
3 - Enter the server's information:

- Description: An optional field to identify the server. 
               Example: Home, Office, Laptop, etc.

- Address (IP): Your Computer's IP address. 

    - Port: The same port number that you choose when start JM2PC Server

    - Password: The same password that you choose when start JM2PC Server

4 - Select (options -> Save) to store this server's profile on the mobile.
5 - Now you have a new server on the server's list. Select (Options -> Connect) 
    to start a connection to the server.
6 - After connection established, a screen with the list of functions will 
    be shown. Get the complete user's guide at http://jm2pc.sourceforge.net 
    to learn how to use all functions.


==============================================================

5. JM2PC BT Client

==============================================================

The JM2PC BT Client is software that must be installed in the mobile phone 
to remote control computers through Bluetooth technology or internet 
connection (GPRS, EDGE or 3G).

ATTENTION: YOU MUST INSTALL THE SAME VERSION NUMBER OF  JM2PC SERVER 
AND JM2PC BT CLIENT.


JM2PC BT Client Installation:
---------------------------------------

You can download the latest version of JM2PC Client at https://sourceforge.net/projects/jm2pc/
(download section). Download JM2PC_BT_Client.jar and JM2PC_BT_Client.jad 
and upload the files to your wireless handheld using a USB cable, 
infrared, or bluetooth. 


Running JM2PC BT Client:
-----------------------------------

Select JM2PC BT Client from applications menu on your mobile phone.


Configurando o JM2PC BT Client:
---------------------------------

To start a connection to a JM2PC Server, you need to add a server profile in 
JM2PC BT Client. 

With JM2PC BT Client you can make Bluetooth or Internet connections to 
JM2PC Server. For Internet connections you can use the same steps as describe in the chapter 4. 

The following steps will show you how to configure JM2PC BT Client 
to connect to JM2PC Server through Bluetooth:

1 - Select (options -> New)
2 - Select connection type - Bluetooth
3 - Select configuration type Automatic (Recommended) or Manual

    3.1 Automatic:

	3.1.1 - Wait while JM2PC BT Client searches for a Bluetooth device 
                with SPP and JM2PC Server.
      
	3.1.2 - After the search is completed, a screen with a list of 
                available Bluetooth Serial Port Profile will be shown. 
                Select the correct service (you can select 
		options -> Info to get more information for each service). 

		Ps.: Server not found? You can use the Manual configuration 
                     (go to section 3.2).
      
	3.1.3 - After you select a service, a screen with all server's 
                information will be shown. You only need to enter the password 
                (the same of JM2PC Server).
      
	3.1.4 - Select (options -> Save) to store this server's profile on the mobile.
      
	3.1.5 - Back to the server's list. Now you have a new server on the server's 
                list. Select (Options -> Connect) to start a connection to the server.

	3.1.6 - After connection established, a screen with the list of functions will 
                be shown. Get the complete user's guide at https://sourceforge.net/projects/jm2pc/ to learn how 
                to use all functions.


    3.2 Manual:

      	3.2.1 - Enter the server's information::

		- Description: An optional field to identify the server. Example: Home, 
                              Office, Laptop, etc.

		- BD Address: The MAC address of the Bluetooth adapter on your computer.  
                              Example: 00:01:3E:D9:A4:29, input without ":" 00013ED9A429.

		What's my bluetooth address? You can find your BD address on your bluetooth 
                software manager configurations.

		- Channel: The channel number of the Serial Port Profile on your Bluetooth 
                           adapter. In the JSR-82 mode this channel is avaliable when you 
			   start the server.
			   Example: 1, 2, 3, 4, 5, 6, 7, 8, 9, etc.

                - Password: The same password that you choose when starts JM2PC Server.
 

	 3.2.2 - Select (options -> Save) to store this server's profile on the mobile.

	 3.2.3 - Back to the server's list. Now you have a new server on the server's list. 
                 Select (Options -> Connect) to start a connection to the server.

	 3.2.4 - After connection established, a screen with the list of functions will 
                 be shown. Get the complete user's guide at www.jm2pc.com to learn how to 
                 use all functions.


==============================================================

6. JM2PC Registration

==============================================================

With JM2PC you can view your desktop on your mobile phone and control the mouse for FREE 
using the unregistered version without problems. 

However, you need to register the software if you need to use all JM2PC features and 
exclusive support, Access www.jm2pc.com (Register section) and read the instructions to buy a license key for JM2PC Software.

==============================================================

7. Plug-ins


Plugins help your JM2PC perform specific functions like direct access and control 
hardware plugged on you computer (like webcam, printers, etc) or fastest control 
of specific software.

You must install plugin on JM2PC Server and the JM2PC Client load the plugin's 
list after establish a connection.

ATTENTION: You need to be a registered user to access plugins in JM2PC Client.


Plugins Installation:
--------------------------

1 - Download the plug-ins at www.jm2pc.com
2 - Copy the .jar file of the plugin to the "JM2PC Server\plugins" directory.
3 - Run the JM2PC Server
4 - Click lug-in button
5 - Enter the plugin ClassName
6 - Click Add button
7 - Click Save button

ATTENTION: In the JM2PC Client (or JM2PC BT Client) go to (options -> Settings) 
and active the option Plug-ins.


How to developer a JM2PC plugin?
--------------------------------------------

If you are a Java developer you can create a plug-in for JM2PC if you need 
to access a specific function in the computer through a mobile phone.

1 - You need to create a class that implements com.jm2pc.Command interface.

Here is the com.jm2pc.Command interface:

package com.jm2pc;

public interface Command {

   String getName();

   String getVersion();

   Object execute(String params) throws Exception;
}

To use this interface you need to implement three methods:
 
String getName(): 

Return the name of your plugin.
ATTENTION: Don't use space ( ( character on the name.

String getVersion(): 

Return the version of your plugin.

Object execute(String params):

This is the principal method of the plugin. The JM2PC call this method when 
JM2PC Client select the plug-in. This method must return a Object like a String, java.awt.Image (the image is automatically render for your mobile phone by the 
JM2PC and will be shown on the mobile screen) or null if you don't send any information to the Client.


Plug-in structure example:


import com.jm2pc.Command;

public class MyPlugin implements Command {

   public MyPlugin() {

      //Plug-in constructor

   }

   public String getName() {
 
      return "MyPluginName";       

   }

   public String getVersion() {

      return "1.0";

   }

   public Object execute(String params) throws Exception {

      // read the parameters...

      // execute some functions

      // return a String, java.awt.Image or null Object


   }

}



To compile your plug-in you need to put the JM2PCommand.jar file 
in your CLASSPATH. This file is on the (JM2PC Server\lib) directory.

Example:

javac -cp "<JM2PC Server installation Path>\lib\JM2PCommand.jar" MyPlugin.java


Now you need to create a jar package from your plugin.

Example:

jar -cvfM MyPlugin.jar MyPlugin.class


Now you can install your plugin on JM2PC.

If you want to share your plugins with others JM2PC users, send an e-mail 
to plugins@jm2pc.com and i publish it on JM2PC official website (www.jm2pc.com). 


==============================================================

8. Contact us

==============================================================

Do you need help? Do you have suggestions? 
Contact us at www.sourceforge.net/projects/jm2pc 

&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
