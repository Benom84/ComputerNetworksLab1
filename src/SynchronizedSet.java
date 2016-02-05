import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;




public class SynchronizedSet<T> implements Iterable<T>{
	
	private Set<T> set;
	
	public SynchronizedSet() {
		set = new HashSet<T>();
	}
	
	public synchronized  void add(T item) throws InterruptedException {
		set.add(item);
		notifyAll();
	}
	
	public synchronized boolean remove(T item) throws InterruptedException {

		return set.remove(item);
	}
	
	public boolean isEmpty(){
		return set.isEmpty();
	}
	
	public synchronized void addAll(Set<T> items) {
		set.addAll(items);
	}

	@Override
	public Iterator<T> iterator() {
		return set.iterator();
	}

	public synchronized int size() {
		return set.size();
	}

	public synchronized boolean contains(T item) {
		
		return set.contains(item);
	}
}
