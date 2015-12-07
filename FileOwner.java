import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Vector;

public class FileOwner implements Runnable {
	
	private Socket socket;
	private static ServerSocket serverSocket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private static Integer TOTAL_CHUNKS;
	private static int numofpeers = 5;
	private static Vector<Integer> sentChunks;
	private static Vector<Vector<Integer>> chunksList = new Vector<Vector<Integer>>();
	private static String FILE_TO_SEND;
	private final static int SERVER_PORT = 8000; // Server listens to this port
	public static int noofChunksSent = 0;
	
	public FileOwner(Socket sock) {
		this.socket = sock;
	}	
	
	/* split owner file to chunks */
	public static int splitFile(File file) {
		int numofPartFiles = 1;
        int sizeofPartFiles = 1024 * 100;
        byte[] buffer = new byte[sizeofPartFiles];
        try {
        	BufferedInputStream bufferedinputstream = new BufferedInputStream(new FileInputStream(file));
        	String filename = file.getName();
            int num = 0;
            while ((num = bufferedinputstream.read(buffer)) > 0) {
            	File newFile = new File(file.getParent(), filename + "." + numofPartFiles);
            	numofPartFiles++;
            	try {
            		FileOutputStream fileoutputstream = new FileOutputStream(newFile);
            		fileoutputstream.write(buffer, 0, num);
            	} catch(IOException io) {
            		io.printStackTrace();
            	}
            }
        } catch(IOException io) {
        	io.printStackTrace();
        }
        return (numofPartFiles-1);
    }
	
	/* allocate chunks to peers */
	public static Vector<Vector<Integer>> allocateChunks() {
		Vector<Vector<Integer>> allocatedList = new Vector<Vector<Integer>>();
		for(int i=0; i<numofpeers; i++) {
			allocatedList.add(new Vector<Integer>());
		}
		for(int i=0; i<TOTAL_CHUNKS; i++) {
			allocatedList.get(i%numofpeers).add(i+1);
		}
		return allocatedList;
	}
	
	public static void main(String args[]) throws Exception {
		FILE_TO_SEND = args[0];											// read the file to send
		TOTAL_CHUNKS = splitFile(new File("server/"+FILE_TO_SEND));		// split the file to send		
		chunksList = allocateChunks();									// allocate chunks to peers
		sentChunks = new Vector<Integer>();
		StartServer();													// start file owner thread
	}
	
	public static void StartServer() throws Exception {
		serverSocket = new ServerSocket(SERVER_PORT, numofpeers);		// initiate new server socket at port 8000
		System.out.println("Listening...");
		while(true) {
			Socket connect = serverSocket.accept();						// accept connection request from peer
			System.out.println("Connecting to "+connect+"...");
			new Thread(new FileOwner(connect)).start();					// start new upload thread for the connected peer
		}
	}
	
	public void run() {
		downloadChunktoPeer();											// start uploading chunks to peer
	}
	
	/* download chunks to the connected peer */
	public void downloadChunktoPeer() {
		try {
			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());
			String rcvdMsg = (String)in.readObject();					// receive the connecting peer number
			Integer peerNum = Integer.parseInt(rcvdMsg);
			System.out.println("Connected to peer "+peerNum);
			out.writeObject(FILE_TO_SEND);								// send file name to peer
			out.flush();
			out.writeObject(TOTAL_CHUNKS.toString());					// send total number of chunks to peer
			out.flush();
			try {
				System.out.println("Chunks to be sent to peer "+peerNum+": "+chunksList.get(peerNum-1));
				
				/* send pre-allocated chunks to the peer */
				for(Integer chunknum = 0; chunknum < chunksList.get(peerNum-1).size(); chunknum++) {
					sendChunk(chunksList.get(peerNum-1).get(chunknum));
					sentChunks.add(chunksList.get(peerNum-1).get(chunknum));	// keep tab of chunks sent
					Thread.sleep(1000);
				}
				
				/* if all chunks have been sent then close server */
				if(sentChunks.size() == TOTAL_CHUNKS) {
					Thread.sleep(5000);
					System.out.println("All chunks have been sent to peers! Shutting down server...");
					System.exit(0);
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		} catch(IOException io) {
			io.printStackTrace();
		} catch(ClassNotFoundException cnf) {
			cnf.printStackTrace();
		} finally {
			try {
				in.close();
				out.close();
			} catch(IOException io) {
				io.printStackTrace();
			}
		}
	}
	
	/* send a particular chunk to the peer */
	public void sendChunk(Integer chunkNum) {
		try {
			out.writeObject(chunkNum.toString());							// send chunk number to peer
			out.flush();
		} catch (IOException io) {
			io.printStackTrace();
		}
		File chunk = new File("server/"+FILE_TO_SEND+"."+chunkNum);
		try {
			byte[] byteArray = Files.readAllBytes(chunk.toPath());
			out.writeObject(byteArray);										// send chunk file to peer
			System.out.println("File "+FILE_TO_SEND+"."+chunkNum+" sent "+"("+byteArray.length+" bytes)");
			out.flush();
		} catch(IOException io) {
			io.printStackTrace();
		}
	}
}
