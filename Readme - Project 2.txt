############################ README ####################################

Internet Protocols - Project 2 - Go-Back-N ARQ

Submitted By:
	Anshul Chandra (achand13)
	Akanksha Shukla (apshukla)

We have provided two folders - One for Client and another for Server.
The folders can be placed in the respective machines to run the client and server as per the choice. For the ease of testing, the client and the server can be placed on the same machine. Also, one than one peer systems can be initiated in the same machine.

These are the commands for compiling and running this project. Please note that the commands are to be executed in the same sequence as provided below.
-----------------------------------------------------------------------
Compilation of Server:
javac FTPPacket.java
javac Server.java

Compilation of Client (to be repeated for each instance):
javac FTPPacket.java
javac Segment.java
javac Client.java
------------------------------------------------------------------------
Running Server:
java Server <port#> <filename> <probability>

Running Peers:
java Client <hostname> <port#> <filename> <window size> <maximum segment size>
------------------------------------------------------------------------

Please note that the file used to transfer from the client to the server is to be placed in the same folder as client program and complete name of the file (including the extension) is to be provided as an argument to the program.
A sample file "text.txt" has been provided in the client folder to test the program.
eg. java Client "localhost" 7735 "test.txt" 64 500