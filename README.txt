Peer-to-Peer Network Design:
============================
The peer to peer network has been designed with 1 File-owner and 5 Peers who download parts of the file from the file owner and exchange among themselves.
The "server" folder contains the files of the file-owner (landscape1.jpg, landscape2.jpg etc.)

Prerequisites:
--------------
The only prerequisite to run the project is having Java SE 1.7 or higher installed in the system.

Execution Steps:
----------------
1. In a command line, navigate to the project directory. Start the file-owner by command "java FileOwner <File Name>"(e.g. "java FileOwner landscape1.jpg", or "java FileOwner landscape2.jpg")
2. In 5 more command lines, type in command "java Peer <Peer Number>. In this case Peer Number runs from 1 to 5. (e.g. "java Peer 1", "java Peer 2" etc). Do not press enter yet in any of the command lines.
3. After you have all 5 command lines ready, now press enter in each of those 5 command lines to launch all the peers simultaneously.
4. The file-owner splits the file into chunks and distributes the chunks in a circular way to all the peers.
5. Each chunk is contained in only one peer before the peers start interacting with each other.
6. The peers get their neighbour information from the network.properties file.
7. The peers store the chunks received from the server or other peers in the "chunks" directory under each peer directory.
8. After receiving all the chunks, the peers merge them and store the merged file in the "mergedfile" directory under each peer directory.

Highest File Size Sent:
-----------------------
The highest file size sent through this peer-to-peer system is 9.70 MB.