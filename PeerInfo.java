package client;

/**
 * Object representation of a peer.
 * Get and set methods are uncommented.
 * 
 * @author thomasbrown
 *
 */
public class PeerInfo {
	private String peerID;
	private String IP;
 	private int port;
	
	public PeerInfo() {
		this.peerID = "";
		this.IP = "";
		this.port = -1;
	}
	
	public PeerInfo(String peerID, String IP, int port) {
		this.peerID = peerID;
		this.IP = IP;
		this.port = port;
	}
	
	public String getPeerID() {
		return this.peerID;
	}
	
	public void setPeerID(String newID) {
		this.peerID = newID;
	}
	
	public String getIP() {
		return this.IP;
	}
	
	//No error checking on whether this is a valid IP.
	public void setIP(String IP) {
		this.IP = IP;
	}
	
	public int getPort() {
		return this.port;
	}

	//No error checking on whether this is a valid port.
	public void setPort(int port) {
		this.port = port;
	}
}
