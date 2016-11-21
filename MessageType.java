package client;

/**
 * Enumerates bittorrent message types.
 * 
 * @author thomas brown
 *
 */
public enum MessageType {
	KEEP_ALIVE,
	CHOKE,
	UNCHOKE,
	INTERESTED,
	NOT_INTERESTED,
	HAVE,
	BITFIELD,
	REQUEST,
	PIECE
}