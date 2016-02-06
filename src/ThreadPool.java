import java.util.ArrayList;


public class ThreadPool {
	
	private boolean isRunning;
	private ArrayList<ThreadedObject> availableThreads;
	private SynchronizedQueue tasks;
	
	public ThreadPool(int maxThreads) {
		
		availableThreads = new ArrayList<ThreadedObject>();
		tasks = new SynchronizedQueue<>();
		isRunning = true;
		for (int i = 0; i < maxThreads; i++) {
			ThreadedObject threadObject = new ThreadedObject(tasks);
			availableThreads.add(threadObject);
			threadObject.start();
		}
	}
	
	public synchronized void exectute(Runnable task) {
		if (!isRunning) {
			System.out.println("ThreadPool was asked to execute when stopped");
		} else {
			try {
				tasks.put(task);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public synchronized void stop() {
		
		isRunning = false;
		for (ThreadedObject threadedObject : availableThreads) {
			threadedObject.StopThread();
		}
	}
	
	public synchronized int Size() {
		return tasks.size();
	}

}
