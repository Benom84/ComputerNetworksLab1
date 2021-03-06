import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/***
 * 
 * A thread safe queue
 *
 * @param <T>
 */
public class SynchronizedQueue<T> implements Iterable<T>{
	
	private LinkedList<T> queue;
	
	public SynchronizedQueue() {
		queue = new LinkedList<T>();
	}
	
	public synchronized  void put(T item) throws InterruptedException {
		queue.add(item);
		notifyAll();
	}
	
	public synchronized T take() throws InterruptedException {
		while (queue.size() == 0) {
			wait();
		}
		
		T item = queue.removeFirst();
		notifyAll();
		return item;
	}
	
	public boolean isEmpty(){
		return queue.isEmpty();
	}
	
	public synchronized int size() {
		return queue.size();
	}
	
	public synchronized void addAll(List<T> items) {
		queue.addAll(items);
		notifyAll();
	}

	@Override
	public Iterator<T> iterator() {
		return queue.iterator();
	}
	
	
}
