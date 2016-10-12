package client;

import GivenTools.*;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.UUID;

public class RUBTClient {
	public static void main(String[] args) {
			byte[] torrentMetadata = null;
			Path pathToTorrent = null;
			String srcTorrent = null;
			String destFile = null;
			TorrentInfo torrentInfo = null;
		
			if (args.length != 2) {
				System.err.println("ERROR: Missing source torrent or destination file.\n"
								 + "Usage: java -cp . RUBTClient <src> <dest>\n");
				return;
			} 
			
			srcTorrent = args[0];
			destFile = args[1];
			
			try {
				pathToTorrent = Paths.get(srcTorrent);
				torrentMetadata = Files.readAllBytes(pathToTorrent);
				torrentInfo = new TorrentInfo(torrentMetadata);
				
				String peerID = generatePeerID();
				URL trackerURL = new URL(torrentInfo.announce_url.toString()
									   + "?" + "info_hash=" + HTTPescape(torrentInfo.info_hash.toString())
									   + "&peer_id=" + HTTPescape(peerID) + "&port=6881&uploaded=0&downloaded=0"
									   + "&left=" + torrentInfo.file_length);
				//URLConnection conn = ();
				//InputStream trackerResponse = conn.getInputStream();
				
			} catch (BencodingException be) {
				System.err.println("ERROR: The specified torrent file is not properly bencoded.\n");
			} catch (IOException ioe) {
				System.err.println("ERROR: An I/O exception occurred trying to read the torrent file.\n"
								 + ioe.getMessage() + ".\n");
			}
	}

	/**
	 * Escapes any characters that could be misinterpreted inside a URL.
	 * Escaping is done by replacing the offending character with the string
	 * "%<hex code point>". For example, the hex code point for the ampersand
	 * is 26 (decimal 38) so "&" is replaced with "%26".
	 * 
	 * Does not escape ASCII control characters or non-ASCII characters: 
	 * 		Code points 00 - 1F
	 * 					7F - 9F
	 * 					A0 - FF
	 * 
	 * because these characters are either control characters or special characters
	 * that would never be encountered in, say, a peer ID or SHA1 hash.
	 * 
	 * @param string
	 * @return HTTP escaped string
	 */
	private static String HTTPescape(String string) {
		
		//The following escapes characters that have special usage in a URL.
		string.replaceAll("%", "%25");
		string.replaceAll("$", "%24");
		string.replaceAll("&", "%26");
		string.replaceAll("+", "%2B");
		string.replaceAll(",", "%2C");
		string.replaceAll("/", "%2F");
		string.replaceAll(":", "%3A");
		string.replaceAll(";", "%3B");
		string.replaceAll("=", "%3D");
		string.replaceAll("?", "%3F");
		string.replaceAll("@", "%40");
		string.replaceAll(" ", "%20");
		string.replaceAll("\"", "%22");
		string.replaceAll("<", "%3C");
		string.replaceAll(">", "%3E");
		string.replaceAll("#", "%23");

		/**
		//Escapes other miscellaneous chars that could present a problem.
		//These cases are likely to never occur, but are included for robustness.
		string.replaceAll("{", "%7B");
		string.replaceAll("}", "%7D");
		string.replaceAll("|", "%7C");
		string.replaceAll("\\", "%5C");
		string.replaceAll("^", "%5E");
		string.replaceAll("~", "%7E");
		string.replaceAll("[", "%5B");
		string.replaceAll("]", "%5D");
		string.replaceAll("`", "%60");
		**/
		
		return string;
	}
	
	private static String generatePeerID() {
		//This is rough to read, so: string "id" is the string RU + the first 18 digits of a random UUID with dashes removed.
		String id = "RU" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 19);
		return id;
	}
}