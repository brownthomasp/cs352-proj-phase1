package client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Handles hash functions.
 * 
 * @author thomas brown
 *
 */
public class HashHandler {	
	/**
	 * Compares hashes contained in two byte arrays for a file or piece of file.
	 * 
	 * @param knownHash = the byte array containing the hash the client has.
	 * @param givenHash = the byte array containing the hash the peer has/the hash calculated from the file we downloaded.
	 * @param knownOffset = the starting index of the hash in the client's byte array.
	 * @param givenOffset = the starting index of the hash in the peer's byte array or calculated byte array.
	 * @return true if the hashes are the same, else false.
	 */
	public static boolean compareHash(byte[] knownHash, byte[] givenHash, int knownOffset, int givenOffset) {
		for (int i = knownOffset, j = givenOffset; i < knownHash.length && i < givenHash.length; i++, j++) {
				if (knownHash[i] != givenHash[j]) {
					return false;
				}
		}
		
		return true;
	}
	
	/**
	 * See above.
	 * 
	 * @param knownHash
	 * @param givenHash
	 * @return true if the hashes are the same, else false.
	 */
	public static boolean compareHash(byte[] knownHash, byte[] givenHash) {
		return compareHash(knownHash, givenHash, 0, 0);
	}
	
	/**
	 * Takes the byte array representation of a hash and outputs a string.
	 * 
	 * @param hash in a byte array
	 * @return a string representing the hash value.
	 */
	public static String convertHashToString(byte[] hash) {
		String hashToString = "";
		
		for (int i = 0; i < hash.length; i++) {
			hashToString += String.format("%02X", hash[i]);
		}
		
		return hashToString;
	} 

	/**
	 * Calculates SHA1 hash for given byte array.
	 * 
	 * @param piece = the byte array whose SHA1 we wish to calculate
	 * @return the SHA1 hash of this array
	 * @throws NoSuchAlgorithmException
	 */
	public static byte[] getSHA1(byte[] piece) throws NoSuchAlgorithmException {
		MessageDigest hasher = MessageDigest.getInstance("SHA-1");
		
		return hasher.digest(piece);
	}
}
