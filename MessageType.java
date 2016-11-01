package client;

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