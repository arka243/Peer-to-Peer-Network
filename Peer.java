import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Vector;


public class Peer implements Runnable{

	private String peeroperation;
	private static int SERVER_PORT;
	private static final String SERVER="127.0.0.1";
	private static int NEIGHBOR_PORT;
	private static int PEER_PORT;
	private static Integer peernum;
	private static ServerSocket servsock;
	private static File[] downloadedChunks;
	private static Vector<Integer> downloadedChunkNumbers;
	private static String FILE_NAME;
	private static Integer numofchunks;
	private Socket downloadsocket;
	private Socket uploadsocket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private static boolean stopReceiving = false;
	
	public Peer(String op) {
		this.peeroperation = op;		// determine the mode of operation
	}
	
	public static void main(String args[]) throws Exception {
		peernum = Integer.parseInt(args[0]);
		Properties properties = new Properties();
		InputStream fileinputstream = new FileInputStream("network.properties");	// read from properties file
		properties.load(fileinputstream);
		String[] input = properties.getProperty(args[0]).split(" ");
		SERVER_PORT = Integer.parseInt(properties.getProperty("server"));	// read server port to download chunks
		PEER_PORT = Integer.parseInt(input[0]);			// read peer port to host upload thread
		NEIGHBOR_PORT = Integer.parseInt(input[1]);		// read neighbor port to connect download thread
		System.out.println("Starting Peer"+peernum+"...");
		new File("peer"+peernum+"/chunks").mkdirs();		// make new sub directory for receiving chunks
		new File("peer"+peernum+"/mergedfile").mkdirs();	// make new sub directory for merged file
		servsock = new ServerSocket(PEER_PORT, 5);
		Peer downloadChunks = new Peer("chunks");			// launch new thread to download chunks from server
		new Thread(downloadChunks).start();
		Thread.sleep(10000);
		Peer setupPeerUpld = new Peer("upload");			// launch new thread to upload chunks to peers
		new Thread(setupPeerUpld).start();
		Peer setupPeerDwnld = new Peer("download");			// launch new thread to download chunks from peers
		new Thread(setupPeerDwnld).start();
	}
	
	public void run() {
		if(peeroperation.equals("chunks")) {
				downloadChunks();							// download chunks from server
		}
		else if(peeroperation.equals("upload")) {
				startPeerUpload();							// upload thread
		}
		else if(peeroperation.equals("download")) {
				startPeerDownload();						// download thread
		}
		else {
			System.out.println("Operation not available! Please re-check operation...");
		}
	}
	
	/* download chunks from server */
	public void downloadChunks() {
		try {
			downloadsocket = new Socket(SERVER, SERVER_PORT);		// initiate new connection request to server
			System.out.println("Connected to server at port "+SERVER_PORT);
			out = new ObjectOutputStream(downloadsocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(downloadsocket.getInputStream());
			out.writeObject(peernum.toString());			// send peer id to server
			out.flush();
			FILE_NAME = (String)in.readObject();			// receive file name from server
			String msg = (String)in.readObject();			// receive total chunks from server
			numofchunks = Integer.parseInt(msg);
			downloadedChunks = new File[numofchunks];
			downloadedChunkNumbers = new Vector<Integer>();
			while(true) {
				try {
					String temp = (String)in.readObject();		// receive the chunk number
					Integer downloadedchunknum = Integer.parseInt(temp);
					File rcvdChunk = new File("peer"+peernum+"/chunks/"+FILE_NAME+"."+downloadedchunknum);
					byte[] byteArray = (byte[]) in.readObject();	// receive the chunk file
					Files.write(rcvdChunk.toPath(), byteArray);
					downloadedChunks[downloadedchunknum-1] = rcvdChunk;		// store received chunk in array
					downloadedChunkNumbers.add(downloadedchunknum);		// keep account of received chunks
					System.out.println("File "+FILE_NAME+"."+downloadedchunknum+" received "+"("+byteArray.length+" bytes)");
				} catch(EOFException eof) {
					System.out.println("Received all chunks from server! Disconnecting");
					break;
				} catch(SocketException se) {
					System.out.println("Received all chunks from server! Disconnecting");
					break;
				} catch(IOException io) {
					io.printStackTrace();
					stopReceiving = true;
				}
				if(allChunksRcvd() || stopReceiving)
					break;
				Thread.sleep(1000);
			}
		} catch(IOException io) {
			io.printStackTrace();
		} catch(ClassNotFoundException cnf) {
			cnf.printStackTrace();
		} catch(InterruptedException ie) {
			ie.printStackTrace();
		} finally {
			try {
				in.close();
				out.close();
			} catch(IOException io) {
				io.printStackTrace();
			}
		}
	}
	
	/* upload thread */
	@SuppressWarnings("unchecked")
	public void startPeerUpload() {
		try {
			uploadsocket = servsock.accept();		// accept connection request from peers
			out = new ObjectOutputStream(uploadsocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(uploadsocket.getInputStream());
			while(true) {
				System.out.println("Chunks "+downloadedChunkNumbers+" available for download from peer "+peernum);
				Vector<Integer> requestedChunks = new Vector<Integer>(); 
				requestedChunks = (Vector<Integer>)in.readObject();		// receive chunk request from peers
				
				/* check if all chunks have been received by upload neighbor */
				if(requestedChunks.contains(0)) {
					System.out.println("Upload Neighbour has received all chunks! Exiting...");
					break;
				}
				else {
					System.out.println("Received Request for chunks: "+requestedChunks);
					Integer chunk = 1;
					boolean findaChunk = false;
					while(!findaChunk) {
						for(Integer i=1; i<=numofchunks; i++) {
							if(downloadedChunkNumbers.contains(i) && requestedChunks.contains(i)) {
								chunk = i;				// select a chunk from requests chunks
								findaChunk = true;
							}
						}
					}
					try {
						System.out.println("Sending chunk "+chunk);
						sendChunktoPeers(chunk);		// send chunk to upload neighbor
						Thread.sleep(1000);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch(SocketException se) {
			System.out.println("Upload Neighbour has received all chunks! Exiting...");
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/* download thread */
	public void startPeerDownload() {
		try {
			downloadsocket = new Socket(SERVER, NEIGHBOR_PORT);		// connect to download neighbor for chunks
			out = new ObjectOutputStream(downloadsocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(downloadsocket.getInputStream());
			while(true) {
				System.out.println("Peer "+peernum+" now has chunks: "+downloadedChunkNumbers);
				if(downloadedChunks != null) {
					/* check if all chunks have been received */
					if(allChunksRcvd()) {
						System.out.println("All chunks received by peer "+peernum+". Merging chunks...");
						mergeFiles(downloadedChunks, new File("peer"+peernum+"/mergedfile/"+FILE_NAME));		//merge file
						Vector<Integer> shutdownDownloadNeighbour = new Vector<Integer>();
						shutdownDownloadNeighbour.add(0);
						out.writeObject(shutdownDownloadNeighbour);			// send shut down request to upload neighbor
						out.flush();
						break;
					}
					else {
						Vector<Integer> remainingChunks = new Vector<Integer>();
						for(Integer i=1; i<=numofchunks; i++) {
							if(!downloadedChunkNumbers.contains(i))
								remainingChunks.add(i);						// check which chunks are yet to receive
						}
						System.out.println("Requested for chunks "+remainingChunks);	
						out.writeObject(remainingChunks);					// send chunk request to upload neighbor
						out.flush();
						String rcvdmsg = (String)in.readObject();			// receive chunk number from upload neighbor
						Integer chunkNo = Integer.parseInt(rcvdmsg);
						File chunkFile = new File("peer"+peernum+"/chunks/"+FILE_NAME+"."+chunkNo);
						byte[] byteArray = (byte[]) in.readObject();		// receive chunk file from upload neighbor
						Files.write(chunkFile.toPath(), byteArray);
						downloadedChunks[chunkNo-1] = chunkFile;			// store file in file array
						downloadedChunkNumbers.add(chunkNo);				// update received chunk list
						System.out.println("File "+FILE_NAME+"."+chunkNo+" received "+"("+byteArray.length+" bytes)");
					}
				}
			}
		} catch(ConnectException ce) {
			System.out.println("Neighbor not connected yet!");
		} catch(IOException io) {
			io.printStackTrace();
		} catch(ClassNotFoundException cnf) {
			cnf.printStackTrace();
		} 
	}
	
	/* check if all chunks are received */
	public boolean allChunksRcvd() {
		for(Integer i=1; i<numofchunks; i++) {
			if(!downloadedChunkNumbers.contains(i))
				return false;
		}
		return true;
	}
	
	/* send chunk to peers */
	public void sendChunktoPeers(Integer chunkNum) {
		try {
			out.writeObject(chunkNum.toString());		// send chunk number to download peer
			out.flush();
		} catch (IOException io) {
			io.printStackTrace();
		}
		File chunk = new File("peer"+peernum+"/chunks/"+FILE_NAME+"."+chunkNum);
		try {
			byte[] byteArray = Files.readAllBytes(chunk.toPath());
			out.writeObject(byteArray);					// send chunk file to download peer
			System.out.println("File "+FILE_NAME+"."+chunkNum+" sent "+"("+byteArray.length+" bytes)");
			out.flush();
		} catch(IOException io) {
			io.printStackTrace();
		}
	}
	
	/* merge files into one file */
	public static void mergeFiles(File[] chunks, File mergedfile) {
		try {
			BufferedOutputStream bufferedoutputstream = new BufferedOutputStream(new FileOutputStream(mergedfile));
			for(File file: chunks) {
				Files.copy(file.toPath(), bufferedoutputstream);
			}
		} catch(IOException io) {
			io.printStackTrace();
		}
	}
}
