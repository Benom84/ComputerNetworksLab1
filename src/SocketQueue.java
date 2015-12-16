import java.net.Socket;
import java.util.LinkedList;


public class SocketQueue {
	
	private LinkedList<Socket> queue;
	
	public SocketQueue() {
		queue = new LinkedList<Socket>();
	}
	
	public synchronized  void put(Socket socket) throws InterruptedException {
		queue.add(socket);
		notifyAll();
	}
	
	public synchronized Socket take() throws InterruptedException {
		while (queue.size() == 0) {
			wait();
		}
		
		Socket socket = queue.removeFirst();
		return socket;
	}
}
