package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * 
 * Peer communication handler.
 * 
 * @author thomas brown
 *
 */
public class PeerCommHandler {
	private Socket peerSocket;
	private DataInputStream peerMessage;
	private DataOutputStream clientMessage;
	
	/**
	 * Handler constructor. Opens a TCP connection to <IP>:<port>, sets keep alive on, and gets the I/O streams
	 * for the connection.
	 * 
	 * @param IP - IP address of peer.
	 * @param port - port of peer.
	 */
	public PeerCommHandler(String IP, int port) {
		try {
			openConnection(IP, port);
			peerSocket.setKeepAlive(true);
			this.peerMessage = new DataInputStream(peerSocket.getInputStream());
			this.clientMessage = new DataOutputStream(peerSocket.getOutputStream());
		} catch (IOException ioe) {
			System.err.println("ERROR: Could not get input or output stream for the given connection."
			                 + "The message was: " + ioe.getMessage());
		}
	}
	
	/**
	 * Closes a connection to the peer.
	 */
	public void closeConnection() {
		try {
			this.clientMessage.close();
			this.peerMessage.close();
			this.peerSocket.close();
		} catch (IOException ioe) {
			System.err.println("ERROR: This peer connection could not be closed.");
		}
	}

	/**
	 * Calculates average RTT over ten connections to a peer specified by the given IP and port.
	 * 
	 * @param IP see above.
	 * @param port see above.
	 */
	public static long getAverageRTT(String IP, int port, byte[] infoHash, String peerID) {
		long begin = 0, end = 0;
		long average = 0;
		
		for (int i = 1; i <= 10; i++) {
			try {
				begin = System.nanoTime();
				
				Socket socket = new Socket(IP, port);
				DataOutputStream clientMessage = new DataOutputStream (socket.getOutputStream());
				DataInputStream peerResponse = new DataInputStream(socket.getInputStream());
				byte[] peerHandshake = new byte[68];
		
				//Send handshake to peer.
				clientMessage.writeByte(19);
				clientMessage.writeBytes("BitTorrent protocol00000000");
				clientMessage.write(infoHash);
				clientMessage.writeBytes(peerID);

				peerResponse.read(peerHandshake);
				
				end = System.nanoTime();
				
				average += (end - begin);
				
				peerResponse.close();
				clientMessage.close();
				socket.close();
			} catch (IOException ioe) {
				System.err.println("ERROR: An I/O error occurred trying to get best peer: " + ioe.getMessage() + "\n");
				ioe.printStackTrace();
			}
		}
		
		System.out.println("Peer " + peerID + " @ " + IP + ":" + port + " had an average RTT of " + average/10 + ".");
		
		return average/10;
	}
	
	/**
	 * Get a message sent by the peer.
	 * 
	 * @return a BTMessage object containing the length prefix, messageID, message type, and payload of this message.
	 * @throws IOException
	 */
	public BTMessage getMessage() throws IOException {
		BTMessage message = new BTMessage();
		byte[] payload = null;
		int length = -1;
		int msgID = -1;
		
		//System.out.println("\tAttempting to read new message length...");
		
		length = peerMessage.readInt();
		message.setLength(length);
		
		//System.out.println("\tRead. Getting message ID...");
		
		if (length != 0) {
			msgID = peerMessage.read();
			message.setMessageID(msgID);
			//System.out.println("\tDone. Parsing message type...");
		}
		
		if (message.getLength() == 1) {
		
			if (message.getMessageID() == 0) {
			
				message.setMessageType(MessageType.CHOKE);
				
			} else if (message.getMessageID() == 1) {
			
				message.setMessageType(MessageType.UNCHOKE);
				
			} else if (message.getMessageID() == 2) {
			
				message.setMessageType(MessageType.INTERESTED);
				
			} else if (message.getMessageID() == 3) {
			
				message.setMessageType(MessageType.NOT_INTERESTED);
				
			}
			
		} else if (message.getLength() == 5 && message.getMessageID() == 4) {
			
			//Skip over the have message. Payload is a constant 4 bytes.
			message.setMessageType(MessageType.HAVE);
			peerMessage.skip(4);
		
		} else if (message.getMessageID() == 5) {
		
			//Skip over the bitfield message (-1 because we already read the message ID, which is included in length)
			message.setMessageType(MessageType.BITFIELD);
			peerMessage.skip(length - 1);
		
		} else if (message.getMessageID() == 7) {
		
			message.setMessageType(MessageType.PIECE);
			payload = new byte[length - 9];		
			
			if (peerMessage.skip(8) == 8) {
			
				peerMessage.readFully(payload, 0, length-9);
				message.setPayload(payload);
				
			}
		
		}
		
		//System.out.println("\tComplete.\n");
		
		return message;
	}
	
	/**
	 * Opens a connection to the peer at <IP>:<port>.
	 * 
	 * @param IP see above.
	 * @param port see above.
	 */
	public void openConnection(String IP, int port) {
		try {
			this.peerSocket = new Socket(IP, port);
		} catch (UnknownHostException uhe) {
			System.err.println("ERROR: The host specified by " + IP + ":" + port + " could not be reached or does not exist.");
		} catch (IOException ioe) {
			System.err.println("ERROR: An error occurred opening a socket to the specified host " + IP + ":" + port + "."
			                 + "The message was: " + ioe.getMessage());
		}
	}

	/**
	 * Unimplemented message to send a bitfield. Not in use at the moment because we are not seeding.
	 */
	public void sendBitfield() {
		System.out.println("\tSending bitfield message to peer.\n");
		return;
	}
	
	/**
	 * Send a choke message.
	 * 
	 * @throws IOException
	 */
	private void sendChoke() throws IOException {
		System.out.println("\tSending choke message to peer.\n");
		clientMessage.writeInt(1);
		clientMessage.writeByte(0);
	}
	
	/**
	 * Send an interested message.
	 * 
	 * @throws IOException
	 */
	private void sendInterested() throws IOException {
		System.out.println("\tSending interested message to peer.\n");
		clientMessage.writeInt(1);
		clientMessage.writeByte(2);
	}
	
	/**
	 * Send a keep-alive message.
	 * @throws IOException
	 */
	private void sendKeepAlive() throws IOException {
		System.out.println("\tSending keep-alive message to peer.\n");
		clientMessage.writeInt(0);
	}
	
	/**
	 * Public-facing interface to send a message of the specified type.
	 * 
	 * @param type - enumerated MessageType of the message we are trying to send.
	 * @throws IOException
	 */
	public void sendMessage(MessageType type) throws IOException {
		switch (type) {
			case KEEP_ALIVE: 
				this.sendKeepAlive();
				break;
			case CHOKE:
				this.sendChoke();
				break;
			case UNCHOKE:
				this.sendUnchoke();
				break;
			case INTERESTED:
				this.sendInterested();
				break;
			case NOT_INTERESTED:
				this.sendNotInterested();
				break;
			default:
				break;
		}
	}
	
	/**
	 * Send a not-interested message.
	 * 
	 * @throws IOException
	 */
	private void sendNotInterested() throws IOException {
		System.out.println("\tSending uninterested message to peer.\n");
		clientMessage.writeInt(1);
		clientMessage.writeByte(3);
	}
	
	/**
	 * Send a request for a new piece/block of the file.
	 * 
	 * @param index - the piece number
	 * @param begin - the byte offset within the piece
	 * @param length - how many bytes to request
	 * @throws IOException
	 */
	public void sendRequest(int index, int begin, int length) throws IOException {
		System.out.println("\tSending request for " + length + " bytes at index " + index + " / offset " + begin + " to peer.\n");
		clientMessage.writeInt(13);
		clientMessage.writeByte(6);
		clientMessage.writeInt(index);
		clientMessage.writeInt(begin);
		clientMessage.writeInt(length);
	}
	
	/**
	 * Send an unchoke message.
	 * 
	 * @throws IOException
	 */
	private void sendUnchoke() throws IOException {
		System.out.println("\tSending unchoke message to peer.\n");
		clientMessage.writeInt(1);
		clientMessage.writeByte(1);
	}
	
	/**
	 * Send a handshake to the peer.
	 * 
	 * @param hashBytes - a byte array containing the hash of this file.
	 * @param peerID - this machine's peer ID.
	 * @throws IOException 
	 */
	public void sendHandshake(byte[] hashBytes, String peerID) throws IOException {
		System.out.println("\tSending handshake to peer.\n");
		clientMessage.writeByte(19);
		clientMessage.writeBytes("BitTorrent protocol00000000");
		clientMessage.write(hashBytes);
		clientMessage.writeBytes(peerID);
	}
	
	/**
	 * Reads peer's handshake and compares the hash given to our known hash.
	 * 
	 * @param hashBytes: byte array representing the SHA1 hash of the file we want.
	 * @return true if the hash validated, false otherwise.
	 * @throws IOException
	 */
	public boolean readHandshakeAndCompare(byte[] hashBytes) throws IOException {
		byte[] handshake = new byte[68];
		peerMessage.readFully(handshake);
		
		if (!HashHandler.compareHash(hashBytes, handshake, 0, 28)) {
			System.err.println("ERROR: could not validate the SHA1 hash provided by the peer against a known hash.\n");
			this.closeConnection();
			return false;
		}
		
		return true;
	}
}
