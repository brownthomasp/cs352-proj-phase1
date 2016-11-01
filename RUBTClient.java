//Thomas Brown

package client;

import GivenTools.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
//import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
//import java.util.Random;
import java.util.UUID;

public class RUBTClient {
	public static void main(String[] args) {

/*-----Variables-----*/

			AbstractList peerList = null;
	
			boolean isNotChoked = false; //Whether the BT connection is choked or not.
	
			byte[] torrentMetadata = null; //Torrent metadata.
			byte[] trackerResponseBytes = null; //Tracker's response to info about a file.
			byte[] peerHandshake = null; //Peer handshake.
			byte[] peerMessage = null; //Peer message.
	
			ByteArrayOutputStream pieceBuffer = new ByteArrayOutputStream(); //Holds the bytes for a piece until we are ready to write to file.
	
			ByteBuffer keyBuffer = null; //Used for looking up keys in the bencoded dictionary of peers.
	
			FileOutputStream fileWriter = null; //Writes file to drive.
	
			InputStream trackerResponse = null; //Tracker's response stream.
			InputStream peerResponse = null; //Peer's response stream to the client.
	
			int blockLength = 0; //The length of the block returned by the peer.
			int bytesLeftForThisFile = 0; //Number of bytes left to download for the file overall.
			int bytesLeftForThisPiece = 0; //Number of bytes left to download for this piece.
			int numPieces = 0; //Number of pieces in the file overall.
			int pieceIndex = 0; //The index of the piece currently downloading, from 0 to numPieces - 1.
			int pieceOffset = 0; //Specifies the byte offset when requesting a new block of this piece.
			int thisPieceLength = 0; //How long this piece is.
	
			long thisPeerRTT = 0;
			long currentBestRTT = -1;
	
			Map responseData = null;
			Map thisPeer = null;
	
			Node<PeerInfo> ruPeers = null;
	
			OutputStream clientMessage = null; //Output stream from the client to the peer.
	
			PeerCommHandler peerCommHandler = null;
	
			PeerInfo thisPeerInfo = null; 
			PeerInfo bestPeer = null; //Peer to whom we have the best (i.e. fastest) connection.
	
			Path pathToTorrent = null; //Path to torrent metadata file.
	
			Socket socket = null; //TCP socket to connect to peer.
	
			String srcTorrent = null; //Command line arg specifying torrent metadata file name.
			String destFile = null; //Command line arg specfiying the file name to save the torrent to.
			String peerID = null; //This client's peerID.
			String infoHash = null; //String represenation of the SHA1 hash of this torrent.
	
			TorrentInfo torrentInfo = null; //Object which holds descriptive info regarding the torrent.
	
			URL trackerURL = null; //URL of the tracker.
	
			URLConnection conn = null; //Connection to the tracker.

/*-----End-Variables-----*/

/*-----Logic-----*/
		
			//Do nothing if src/dest not specified.
			if (args.length != 2) {
				System.err.println("ERROR: Missing source torrent or destination file.\n"
                                 + "Usage: java -cp . RUBTClient <src> <dest>\n");
				return;
			} 
			
			srcTorrent = args[0];
			destFile = args[1];
			
			System.out.println("---BEGIN DEBUG LOG---\n");
			
			try {
				//Get path to torrent file and read it into a new TorrentInfo object. Extract needed data from this object.
				pathToTorrent = Paths.get(srcTorrent);
				
				torrentMetadata = Files.readAllBytes(pathToTorrent);
				torrentInfo = new TorrentInfo(torrentMetadata);
				
				numPieces = torrentInfo.piece_hashes.length;
				bytesLeftForThisFile = torrentInfo.file_length;
				thisPieceLength = torrentInfo.piece_length;
				
				//Create a new unique peer ID and get the SHA1 hash of the torrent in string form, for passing via URL.
				peerID = generatePeerID();
				infoHash = HashHandler.convertHashToString(torrentInfo.info_hash.array());
				
				//Create new URL for the tracker and connect.
				trackerURL = new URL(torrentInfo.announce_url.toString()
                                       + "?" + "info_hash=" + HTTPescape(infoHash)
                                       + "&peer_id=" + peerID + "&port=6881&uploaded=0&downloaded=0"
                                       + "&left=" + torrentInfo.file_length + "&event=started");
				conn = trackerURL.openConnection();
				
				//Get tracker's response incl. peer list and read into a local byte array.
				trackerResponseBytes = new byte[conn.getContentLength()];
				trackerResponse = conn.getInputStream();
				trackerResponse.read(trackerResponseBytes);
				
				try {
					//Decode a map from the tracker's response. 
					//Bencoder2.decode actually has return type "object", but we know a proper response from the tracker is a map.
					responseData = (Map)Bencoder2.decode(trackerResponseBytes);

					keyBuffer = ByteBuffer.wrap(new byte[]{'p', 'e', 'e', 'r', 's'});
						
					if (responseData.containsKey(keyBuffer)) {
						peerList = (AbstractList) responseData.get(keyBuffer);
					} else {
						System.err.println("ERROR: tracker response did not include a list of peers.\n");
						return;
					}
					
					//Go over the list of peers and get all that begin with RU.
					for (Iterator i = peerList.iterator(); i.hasNext();) {
						thisPeer = (Map)i.next();
						
						ByteBuffer peerIDKeyBuffer = ByteBuffer.wrap(new byte[]{'p', 'e', 'e', 'r', ' ', 'i', 'd'});
						ByteBuffer peerIPKeyBuffer = ByteBuffer.wrap(new byte[]{'i', 'p'});
						ByteBuffer peerPortKeyBuffer = ByteBuffer.wrap(new byte[]{'p', 'o', 'r', 't'}); 
						
						//Extract peer ID in string form.
						ByteBuffer thisPeerIDBuffer = (ByteBuffer) thisPeer.get(peerIDKeyBuffer);
						String thisPeerID = new String(thisPeerIDBuffer.array());
						
						//If this is an RU peer, store its info into a list of peers.
						if (thisPeerID.substring(0, 3).equals("-RU")) {
							thisPeerInfo = new PeerInfo();
							thisPeerInfo.setPeerID(thisPeerID);
							
							ByteBuffer peerIPToString = (ByteBuffer) thisPeer.get(peerIPKeyBuffer);
							
							thisPeerInfo.setIP(new String(peerIPToString.array()));
							thisPeerInfo.setPort((int) thisPeer.get(peerPortKeyBuffer));
							
							if (ruPeers == null) {
								ruPeers = new Node<PeerInfo>(thisPeerInfo, null);
							} else {
								ruPeers = ruPeers.insert(new Node<PeerInfo>(thisPeerInfo, null));
							}
						}
					} 
					
					for (Node<PeerInfo> ptr = ruPeers; ptr != null; ptr = ptr.getNext()) {
						System.out.println("\tPeerID: " + ptr.getData().getPeerID());
						System.out.println("\tIP Address: " + ptr.getData().getIP());
						System.out.println("\tPort: " + ptr.getData().getPort() + "\n");
						
						thisPeerRTT = PeerCommHandler.getAverageRTT(ptr.getData().getIP(), ptr.getData().getPort(), torrentInfo.info_hash.array(), peerID);
						
						if (currentBestRTT == -1 || thisPeerRTT < currentBestRTT) {
							System.out.println("\tThis peer (" + thisPeerRTT + ") is a better peer than the current best (" + currentBestRTT + ").");
							currentBestRTT = thisPeerRTT;
							bestPeer = ptr.getData();
						}
						
						System.out.println("\tNew/current best is " + currentBestRTT + "\n");
					}
					
					System.out.println("\tBest Peer:");
					System.out.println("\t\tPeerID: " + bestPeer.getPeerID());
					System.out.println("\t\tIP Address: " + bestPeer.getIP());
					System.out.println("\t\tPort: " + bestPeer.getPort() + "\n");
					
					trackerResponse.close();

				} catch (BencodingException be) {
					System.err.println("ERROR: The tracker's response was not properly bencoded.\n"
                                     + be.getMessage());
				}
				
				System.out.println("\tEstablishing connection with best peer.\n");
				peerCommHandler = new PeerCommHandler(bestPeer.getIP(), bestPeer.getPort());
				peerCommHandler.sendHandshake(torrentInfo.info_hash.array(), peerID);
				
				if (peerCommHandler.readHandshakeAndCompare(torrentInfo.info_hash.array()) == false) {
					System.err.println("ERROR: peer did not handshake with the correct hash.");
					return;
				} else {
					peerCommHandler.sendMessage(MessageType.INTERESTED);
					System.out.println("\tSent interested message.\n");
				}

				fileWriter = new FileOutputStream(destFile);

				peerMessage = new byte[32768];
				bytesLeftForThisPiece = torrentInfo.piece_length;
				
				//While there are still bytes left:
				while (bytesLeftForThisFile > 0) {
				
					BTMessage message = peerCommHandler.getMessage();
					
					if (message.getType() == MessageType.KEEP_ALIVE) {
						System.out.println("\tReceived keep-alive message. Taking no action.");
						//again, do nothing for now. I have auto-keep alive set for this connection.
					}
					
					if (message.getType() == MessageType.HAVE || message.getType() == MessageType.BITFIELD) {
						System.out.println("\tReceived \"have\" or bitfield message. Taking no action (for now).");
						//do nothing for now. a have or bitfield message will be useful for the next project phase, but the RU peer definitely has everything.
					}

					if (isNotChoked) {
					
						if (message.getType() == MessageType.CHOKE) {
							System.out.println("\tReceived choke message. Waiting for unchoke.");
							isNotChoked = false;
							
							continue;
							
						//Otherwise, check if we get a "block" message.
						} else if (message.getType() == MessageType.PIECE) {
							System.out.println("\tGot data; attempting to write this block.");
							
							//Write block into buffer and get the size of it.
							pieceBuffer.write(message.getPayload());
							blockLength = message.getLength() - 9;
							
							//Get how many bytes are left to go for this piece and what our "offset" is 
							bytesLeftForThisPiece -= blockLength;
							pieceOffset += blockLength;
							
							System.out.println("\tThere are " + bytesLeftForThisPiece + " bytes left for this piece; we are looking for bytes now starting at offset "
							+ pieceOffset + " bytes within this piece.") ;
							
							if (bytesLeftForThisPiece == 0) {
								
								//Only try to request next piece if SHA1 hashes match.
								if (HashHandler.compareHash(torrentInfo.piece_hashes[pieceIndex].array(), HashHandler.getSHA1(pieceBuffer.toByteArray()))) {
									System.out.println("Hash of received piece compared to known hash with a successful match. Writing to file.");
									pieceIndex++;
									bytesLeftForThisFile -= thisPieceLength;
									fileWriter.write(pieceBuffer.toByteArray());
									
									System.out.println("\tWill try to download next piece with index " + pieceIndex + ". There are " 
									+ bytesLeftForThisFile + " bytes left to download.");
									
									//If we have less bytes left than the standard piece length, go ahead and set piece length to bytes left
									if (torrentInfo.piece_length > bytesLeftForThisFile) {
										thisPieceLength = bytesLeftForThisFile;
									} else {
										thisPieceLength = torrentInfo.piece_length;
									}
								} else {
									System.out.println("\tHash of received piece did not match known hash. Resetting and attempting to re-download.");
									bytesLeftForThisPiece = thisPieceLength;
								}
							
								//Flush the buffer so we can reuse it and send our offset back to zero.
								pieceOffset = 0;
								pieceBuffer.reset();
							}
							
							//If we still have pieces to go.
							if (pieceIndex < numPieces) {	
								System.out.println("\tRequesting 16,384 bytes @ piece index " + pieceIndex + " and piece offset " + pieceOffset + " bytes.");
								peerCommHandler.sendRequest(pieceIndex, pieceOffset, 16384);
							}
						}
					} else {
						//If we get an unchoke message.
						if (message.getType() == MessageType.UNCHOKE) {
							System.out.println("\tUnchoke received.");
							System.out.println("\tRequesting 16,384 bytes @ piece index " + pieceIndex + " and piece offset " + pieceOffset + " bytes.");
							peerCommHandler.sendRequest(pieceIndex, pieceOffset, 16384);
							isNotChoked = true;
						}
					}
				}
				
				pieceBuffer.close();
				fileWriter.close();
				peerCommHandler.closeConnection();
			} catch (BencodingException be) {
				System.err.println("ERROR: The specified torrent file is not properly bencoded.\n");
			} catch (IOException ioe) {
				System.err.println("ERROR: An I/O error occured with error message \"" + ioe.getMessage() + "\"\n");
			} catch (NoSuchAlgorithmException nsae) {
				System.err.println("ERROR: Could not calculate the SHA1 hash of a piece of the torrent.\n"
                                 + nsae.getMessage() + "\n");
			}
			
			System.out.println("---END DEBUG LOG---");
/*-----End-Logic-----*/
	}

	/**
	 * Generates a 20-char unique peer ID.
	 * 
	 * @return the peer ID.
	 */
	private static String generatePeerID() {
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