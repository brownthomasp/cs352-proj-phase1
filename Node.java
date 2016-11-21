package client;

/**
 * Generic linked list implementation.
 * Get and set methods are uncommented.
 * 
 * @author thomas brown
 *
 * @param <T> generic type.
 */
public class Node<T> {
	private T data;
	private Node<T> next;
	
	public Node() {
		data = null;
		this.next = null;
	}
	
	public Node(T data, Node<T> next) {
		this. data = data;
		this.next = next;
	}
	
	public T getData() {
		return this.data;
	}
	
	public Node<T> getNext() {
		return this.next;
	}
	
	public void setNext(Node<T> next) {
		this.next = next;
	}
	
	/**
	 * Inserts a new node into the given list, and returns the new node as the head of the list.
	 * 
	 * @param newNode - the node to be inserted
	 * @return a pointer to the head of the list
	 */
	public Node<T> insert(Node<T> newNode) {
		newNode.setNext(this);
		
		return newNode;
	}
	
	public int getListLength(Node <T> head) {
		int length = 0;
		
		for (Node<T> ptr = head; ptr != null; ptr = ptr.getNext()) {
			length++;
		}
		
		return length;
	}
}