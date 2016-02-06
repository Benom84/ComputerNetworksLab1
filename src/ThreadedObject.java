
public class ThreadedObject extends Thread {

	private boolean isRunning;
	private SynchronizedQueue tasks;
	
	public ThreadedObject(SynchronizedQueue tasks) {
		
		this.tasks = tasks;
		isRunning = true;
	}
	
	public void run() {
		
		while (isRunning) {
			
				Runnable runnable;
				try {
					runnable = (Runnable) tasks.take();
					runnable.run();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
			
		}
	}
	
	public void StopThread() {
		
		isRunning = false;
		interrupt();
		
	}
	
	public boolean isRunning() {
		return isRunning;
	}

}
