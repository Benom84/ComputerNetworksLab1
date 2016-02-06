
public class ThreadConnection<T> {
	public Thread thread;
	public T threadedClass;
	
	public ThreadConnection(Thread thread, T connectedClass) {
		this.thread = thread;
		this.threadedClass = connectedClass;
	}

}
