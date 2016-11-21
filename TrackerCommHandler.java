package client;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.UUID;

import GivenTools.TorrentInfo;

/**
 * Tracker communication handler.
 * 
 * @author thomas brown
 *
 */
public class TrackerCommHandler {
	private String peerID;
	private TorrentInfo torrentInfo;
	private URL trackerURL;
	private URLConnection conn;
	private InputStream trackerResponse;

	public TrackerCommHandler() {
		this.peerID = "";
		this.torrentInfo = null;
		this.trackerURL = null;
		this.conn = null;
	}
	
	public TorrentInfo getTorrentInfo() {
		return this.torrentInfo;
	}
	
	public void setTorrentInfo(TorrentInfo torrentInfo) {
		this.torrentInfo = torrentInfo;
	}
	
	public String getPeerID() {
		return this.peerID;
	}
	
	/**
	 * Sets a peerID to a new randomly generated peerID.
	 */
	public void setPeerID() {
		this.peerID = generatePeerID();
	}
	
	/**
	 * Sets a peerID to the given peerID.
	 * @param peerID
	 */
	public void setPeerID(String peerID) {
		this.peerID = peerID;
	}

	public URL getURL() {
		return this.trackerURL;
	}
	
	public void setURL(URL newURL) {
		this.trackerURL = newURL;
	}
	
	/**
	 * Establishes an initial connection to the tracker to request the list of peers for a file.
	 * 
	 * @throws IOException
	 */
	public void connect() throws IOException {
		try {
			this.trackerURL = new URL(this.torrentInfo.announce_url.toString()
			                        + "?" + "info_hash=" + HTTPescape(HashHandler.convertHashToString(this.torrentInfo.info_hash.array()))
			                        + "&peer_id=" + this.peerID + "&port=6881&uploaded=0&downloaded=0"
			                        + "&left=" + this.torrentInfo.file_length + "&event=started");
			this.conn = trackerURL.openConnection();
		} catch (MalformedURLException muee) {
			System.err.println("ERROR: Could not connect to the tracker. The specified URL was malformed.\n"
			                 + "Message: " + muee.getMessage());
		}
	}
	
	/**
	 * Reads the response of the tracker to a request and returns it in byte array form.
	 * 
	 * @return the tracker's response.
	 * @throws IOException
	 */
	public byte[] readResponse() throws IOException {
		this.trackerResponse = this.conn.getInputStream();
		byte[] trackerResponseBytes = new byte[this.conn.getContentLength()];
		this.trackerResponse.read(trackerResponseBytes);
		
		return trackerResponseBytes;
	}
	
	
	/**
	 * Sends a message to the tracker that this file finished downloading.
	 * 
	 * @throws IOException
	 */
	public void sendCompleted() throws IOException {
		try {
			this.trackerURL = new URL(this.torrentInfo.announce_url.toString()
			                        + "?" + "info_hash=" + HTTPescape(HashHandler.convertHashToString(this.torrentInfo.info_hash.array()))
			                        + "&peer_id=" + this.peerID + "&port=6881&uploaded=0&downloaded=" + this.torrentInfo.file_length
			                        + "&left=0&event=completed");
			this.conn = trackerURL.openConnection();
		} catch (MalformedURLException muee) {
			System.err.println("ERROR: Could not connect to the tracker. The specified URL was malformed.\n"
			                 + "Message: " + muee.getMessage());
		}
	}
	
	/**
	 * Generates a 20-char unique peer ID.
	 * 
	 * @return the peer ID.
	 */
	public static String generatePeerID() {
		String id = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 20);
		return id;
	}

	/**
	 * Escapes any hex values found in a string so that the string can be used in a URL.
	 * 
	 * @param string = the string to be escaped
	 * @return an HTTP escaped string
	 */
	private static String HTTPescape(String string) {
		String escapedString = "";
		
		for (int i = 0; i < string.length(); i += 2) {
			String substring = string.substring(i, i + 2);
			
			if (Integer.parseInt(substring, 16) != -1) {
					escapedString += "%" + substring;
			} else {
				escapedString += substring;
			}
		}
		
		return escapedString;
	}
}
