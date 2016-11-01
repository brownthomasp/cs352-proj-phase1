//Thomas Brown

package client;

public class BTMessage {
	private MessageType type;
	private int length;
	private int msgID;
	private byte[] payload;
	
	public BTMessage() {
		this.type = null;
		this.length = -1;
		this.msgID = -1;
		this.payload = null;
	}
	
	public BTMessage(MessageType type, int length, int msgID, byte[] payload) {
		this.type = type;
		this.length = length;
		this.msgID = msgID;
		this.payload = payload;
	}
	
	public MessageType getType() {
		return this.type;
	}
	
	public void setMessageType(MessageType type) {
		this.type = type;
	}
	
	public int getMessageID() {
		return this.msgID;
	}
	
	public void setMessageID(int msgID) {
		this.msgID = msgID;
	}
	
	public int getLength() {
		return this.length;
	}
	
	public void setLength(int length) {
		this.length = length;
	}
	
	public byte[] getPayload() {
		return this.payload;
	}
	
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
}