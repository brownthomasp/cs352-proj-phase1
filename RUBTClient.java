package client;

import GivenTools.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.Map;

/**
 * Main RU bittorrent client. RU New Brunswick, CS352, Fall 2016
 * 
 * @author thomas brown
 *
 */
public class RUBTClient {
	public static void main(String[] args) {

/*-----Variables-----*/

			AbstractList peerList = null; //A list of peers, extracted from the tracker response.
	
			boolean isNotChoked = false; //Whether the BT connection is choked or not.
	
			byte[] torrentMetadata = null; //Torrent metadata.
			byte[] trackerResponseBytes = null; //Tracker's response to info about a file.
	
			ByteArrayOutputStream pieceBuffer = new ByteArrayOutputStream(); //Holds the bytes for a piece until we are ready to write to file.
	
			ByteBuffer keyBuffer = null; //Used for looking up keys in the bencoded dictionary of peers.
	
			FileOutputStream fileWriter = null; //Writes file to drive.
	
			int blockLength = 0; //The length of the block returned by the peer.
			int bytesLeftForThisFile = 0; //Number of bytes left to download for the file overall.
			int bytesLeftForThisPiece = 0; //Number of bytes left to download for this piece.
			int numPieces = 0; //Number of pieces in the file overall.
			int pieceIndex = 0; //The index of the piece currently downloading, from 0 to numPieces - 1.
			int pieceOffset = 0; //Specifies the byte offset when requesting a new block of this piece.
			int thisPieceLength = 0; //How long this piece is.
	
			long downloadStart = 0; //Time when download began.
			long downloadEnd = 0; //Time when download ended.
			long thisPeerRTT = 0; //RTT to current peer.
			long currentBestRTT = -1; //RTT to best peer, at this time.
	
			Map responseData = null; //Map of K,V pairs that the tracker sent back.
			Map thisPeer = null; //Map from this peer to it's various fields (IP, port, peerID)
	
			Node<PeerInfo> ruPeers = null; //Head of a linked list of PeerInfo objects.
	
			PeerCommHandler peerCommHandler = null; //Handler class for communication with the peer.
	
			PeerInfo thisPeerInfo = null; //Object representing a peer with fields (IP, port, peerID)
			PeerInfo bestPeer = null; //Peer to whom we have the best (i.e. fastest) connection.
	
			Path pathToTorrent = null; //Path to torrent metadata file.
	
			String srcTorrent = null; //Command line arg specifying torrent metadata file name.
			String destFile = null; //Command line arg specfiying the file name to save the torrent to.
	
			TorrentInfo torrentInfo = null; //Object which holds descriptive info regarding the torrent.
	
			TrackerCommHandler trackerCommHandler = null; //Handler class for communication with the tracker.

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
			
			System.out.println("-----BEGIN-LOG-----\n");
			
			try {
				//Get path to torrent file and read it into a new TorrentInfo object. Extract needed data from this object.
				pathToTorrent = Paths.get(srcTorrent);
				
				torrentMetadata = Files.readAllBytes(pathToTorrent);
				torrentInfo = new TorrentInfo(torrentMetadata);
				
				numPieces = torrentInfo.piece_hashes.length;
				bytesLeftForThisFile = torrentInfo.file_length;
				thisPieceLength = torrentInfo.piece_length;
				
				//Create a new unique peer ID and get the SHA1 hash of the torrent in string form, for passing via URL.
				trackerCommHandler = new TrackerCommHandler();
				trackerCommHandler.setPeerID();
				trackerCommHandler.setTorrentInfo(torrentInfo);
				trackerCommHandler.connect();
				trackerResponseBytes = trackerCommHandler.readResponse();
				
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
						if (thisPeerID.substring(0, 7).equals("-RU1103")) {
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
						
						thisPeerRTT = PeerCommHandler.getAverageRTT(ptr.getData().getIP(), ptr.getData().getPort(), torrentInfo.info_hash.array(), trackerCommHandler.getPeerID());
						
						if (currentBestRTT == -1 || thisPeerRTT < currentBestRTT) {
							currentBestRTT = thisPeerRTT;
							bestPeer = ptr.getData();
						}
					}
					
					System.out.println("\n\tBest Peer:");
					System.out.println("\t\tPeerID: " + bestPeer.getPeerID());
					System.out.println("\t\tIP Address: " + bestPeer.getIP());
					System.out.println("\t\tPort: " + bestPeer.getPort() + "\n");
					
				} catch (BencodingException be) {
					System.err.println("ERROR: The tracker's response was not properly bencoded.\n"
                                     + be.getMessage());
				}
				
				System.out.println("\tEstablishing connection with best peer.\n");
				peerCommHandler = new PeerCommHandler(bestPeer.getIP(), bestPeer.getPort());
				peerCommHandler.sendHandshake(torrentInfo.info_hash.array(), trackerCommHandler.getPeerID());
				
				if (peerCommHandler.readHandshakeAndCompare(torrentInfo.info_hash.array()) == false) {
					System.err.println("ERROR: peer did not handshake with the correct hash.");
					return;
				}

				fileWriter = new FileOutputStream(destFile);
				
				bytesLeftForThisPiece = torrentInfo.piece_length;
				
				downloadStart = System.nanoTime();
				
				//While there are still bytes left:
				while (bytesLeftForThisFile > 0) {
				
					BTMessage message = peerCommHandler.getMessage();
					System.out.println("\tNew message received.");
					message.print();
					System.out.println("\n\tBytes left for this file: " + bytesLeftForThisFile);
					System.out.println("\tPieces left for this file: " + (torrentInfo.piece_hashes.length - pieceIndex));
					System.out.println("\tCurrent piece: " + pieceIndex);
					System.out.println("\tCurrent byte offset, within the current piece: " + pieceOffset);
					System.out.println("\tNumber of bytes left to download for this piece: " + bytesLeftForThisPiece + "\n");

					if (message.getType() == MessageType.HAVE || message.getType() == MessageType.BITFIELD) {
						peerCommHandler.sendMessage(MessageType.INTERESTED);
					}

					if (isNotChoked) {
					
						//If we get choked, wait.
						if (message.getType() == MessageType.CHOKE) {
							System.out.println("\tReceived choke message. Waiting for unchoke.\n");
							isNotChoked = false;
							
							continue;
							
						//Otherwise, check if we get a "block" message.
						} else if (message.getType() == MessageType.PIECE) {
							
							//Write block into buffer and get the size of it.
							pieceBuffer.write(message.getPayload());
							blockLength = message.getLength() - 9;
							
							//Get how many bytes are left to go for this piece and what our "offset" is 
							bytesLeftForThisPiece -= blockLength;
							pieceOffset += blockLength;
							
							if (bytesLeftForThisPiece == 0) {
								
								//Only try to request next piece if SHA1 hashes match.
								if (HashHandler.compareHash(torrentInfo.piece_hashes[pieceIndex].array(), HashHandler.getSHA1(pieceBuffer.toByteArray()))) {
									System.out.println("\tHash of received piece compared to known hash with a successful match. Writing to file.\n");
									pieceIndex++;
									bytesLeftForThisFile -= thisPieceLength;
									pieceOffset = 0;
									fileWriter.write(pieceBuffer.toByteArray());
										
									//If we have less bytes left than the standard piece length, go ahead and set piece length to bytes left
									if (torrentInfo.piece_length > bytesLeftForThisFile) {
										thisPieceLength = bytesLeftForThisFile;
										bytesLeftForThisPiece = thisPieceLength;
									} else {
										thisPieceLength = torrentInfo.piece_length;
										bytesLeftForThisPiece = thisPieceLength;
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
								if (bytesLeftForThisPiece >= 16384) {
									peerCommHandler.sendRequest(pieceIndex, pieceOffset, 16384);
								} else {
									peerCommHandler.sendRequest(pieceIndex, pieceOffset, bytesLeftForThisPiece);
								}
							}
						}
					} else {
						//If we get an unchoke message.
						if (message.getType() == MessageType.UNCHOKE) {
							//System.out.println("\tUnchoke received.\n");
							isNotChoked = true;
							peerCommHandler.sendRequest(pieceIndex, pieceOffset, bytesLeftForThisPiece);
						}
					}
				}
				
				downloadEnd = System.nanoTime();
				
				fileWriter.close();
				peerCommHandler.closeConnection();
				trackerCommHandler.sendCompleted();
				
				System.out.println("\tDownload completed and all connections closed. Tracker notified of download completion.");
				System.out.println("\tThis download took approximately " + Math.round(((downloadEnd - downloadStart) / 1000000000.0)) + " seconds.\n");
			} catch (BencodingException be) {
				System.err.println("ERROR: The specified torrent file is not properly bencoded.\n");
			} catch (IOException ioe) {
				System.err.println("ERROR: An I/O error occured with error message \"" + ioe.getMessage() + "\"\n");
				ioe.printStackTrace();
			} catch (NoSuchAlgorithmException nsae) {
				System.err.println("ERROR: Could not calculate the SHA1 hash of a piece of the torrent.\n"
                                 + nsae.getMessage() + "\n");
			}
			
			System.out.println("-----END-LOG-----");
/*-----End-Logic-----*/
	}
}