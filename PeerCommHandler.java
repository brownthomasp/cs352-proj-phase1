package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class PeerCommHandler {
	private Socket peerSocket;
	private DataInputStream peerMessage;
	private DataOutputStream clientMessage;
	

	
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
				OutputStream clientMessage = socket.getOutputStream();
				InputStream peerResponse = socket.getInputStream();
		
				//Send handshake to peer.
				clientMessage.write((byte) 19);
				clientMessage.write("BitTorrent protocol00000000".getBytes());
				clientMessage.write(infoHash);
				clientMessage.write(peerID.getBytes());
				
				byte[] peerHandshake = new byte[68];
				peerResponse.read(peerHandshake);
				
				end = System.nanoTime();
				
				average += (end - begin);
				socket.close();
			} catch (IOException ioe) {
				System.err.println("ERROR: An I/O error occurred trying to open a TCP socket to a peer: " + ioe.getMessage() + "\n");
				ioe.printStackTrace();
			}
		}
		
		return average/10;
	}
	
	public BTMessage getMessage() throws IOException {
		BTMessage message = new BTMessage();
		byte[] messageBytes = new byte[4]; 
		ByteBuffer buffer = null;
		int length = -1;
		int msgID = -1;
		
		peerMessage.read(messageBytes, 0, 4);
		buffer = ByteBuffer.wrap(messageBytes, 0, 4);
		System.out.println("\tReceived new message reading:");
		for (int i = 0; i < messageBytes.length; i++) {
			System.out.println("\t" + Byte.toString(messageBytes[i]));
		}
		message.setLength(buffer.getInt());
		
		if (length != 0) {
			messageBytes = new byte[1];
			peerMessage.read(messageBytes, 0, 1);
			System.out.println("\t" + Byte.toString(messageBytes[0]) + "\n");
			buffer = ByteBuffer.wrap(messageBytes);
			message.setMessageID(buffer.get());
		}
		
	//	System.out.println("\tRead this message's length as " + length + " and msgID as (will be -1 if keep-alive) " + msgID + ".");
		
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
			
			message.setMessageType(MessageType.HAVE);
			messageBytes = new byte[message.getLength() - 1];
			peerMessage.read(messageBytes, 0, message.getLength() - 1);
			message.setPayload(messageBytes);
		
		} else if (message.getMessageID() == 5) {
		
			message.setMessageType(MessageType.BITFIELD);
			messageBytes = new byte[message.getLength() - 1];
			peerMessage.read(messageBytes, 0, message.getLength() - 1);
			message.setPayload(messageBytes);
		
		} else if (message.getMessageID() == 7) {
		
			message.setMessageType(MessageType.PIECE);
			messageBytes = new byte[message.getLength() - 9];
		
			if (peerMessage.skip(8) == 8) {
				peerMessage.read(messageBytes, 0, message.getLength() - 9);
				message.setPayload(messageBytes);
			}
		
		}
		
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
	
	public void sendBitfield() {
	
	}
	
	private void sendChoke() throws IOException {
		clientMessage.write(ByteBuffer.allocate(4).putInt(1).array());
		clientMessage.write((byte) 0);
	}
	
	private void sendInterested() throws IOException {
		byte[] b = ByteBuffer.allocate(4).putInt(1).array();
		System.out.println("\tNext message reads:");
		for (int i = 0; i < b.length; i++) {
			System.out.println("\t" + Byte.toString(b[i]));
		}
		clientMessage.write(ByteBuffer.allocate(4).putInt(1).array());
		clientMessage.write((byte) 2);
		System.out.println("\t" + Byte.toString((byte) 2) + "\n");
		
	}
	
	private void sendKeepAlive() throws IOException {
		clientMessage.write(ByteBuffer.allocate(4).putInt(0).array());
	}
	
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
	
	private void sendNotInterested() throws IOException {
		clientMessage.write(ByteBuffer.allocate(4).putInt(1).array());
		clientMessage.write((byte) 3);
	}
	
	public void sendRequest(int index, int begin, int length) throws IOException {
		clientMessage.write(ByteBuffer.allocate(4).putInt(13).array());
		clientMessage.write((byte) 6);
		clientMessage.write(ByteBuffer.allocate(4).putInt(index).array());
		clientMessage.write(ByteBuffer.allocate(4).putInt(begin).array());
		clientMessage.write(ByteBuffer.allocate(4).putInt(length).array());
	}
	
	private void sendUnchoke() throws IOException {
		clientMessage.write(ByteBuffer.allocate(4).putInt(1).array());
		clientMessage.write((byte) 1);
	}
	
	public void sendHandshake(byte[] hashBytes, String peerID) throws IOException {
		clientMessage.write((byte) 19);
		clientMessage.write("BitTorrent protocol00000000".getBytes());
		clientMessage.write(hashBytes);
		clientMessage.write(peerID.getBytes());
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
		peerMessage.read(handshake);
		
		if (!HashHandler.compareHash(hashBytes, handshake, 0, 28)) {
			System.err.println("ERROR: could not validate the SHA1 hash provided by the peer against a known hash.\n");
			this.closeConnection();
			return false;
		}
		
		return true;
	}
	
	public void readMessage() {
	
	}
}
