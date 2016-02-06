
public class AnalyzersManager implements Runnable{
	
	private Crawler parentCrawler;
	private int maxAnalyzers;
	private Boolean isActive;
	private SynchronizedQueue<HTMLContent> HtmlsToAnalyze;
	private SynchronizedQueue<ThreadConnection<Analyzer>> availableThreads;
	private SynchronizedSet<ThreadConnection<Analyzer>> workingThreads;

	
	public AnalyzersManager(int maxThreads, Crawler crawler, SynchronizedQueue<HTMLContent> HtmlsToAnalyze) {
		
		parentCrawler = crawler;
		maxAnalyzers = maxThreads;
		this.HtmlsToAnalyze = HtmlsToAnalyze;
		isActive = true;
		
		availableThreads = new SynchronizedQueue<>();
		workingThreads = new SynchronizedSet<>();
		
		for (int i = 0; i < maxAnalyzers; i++) {
			
			Analyzer analyzer = new Analyzer(parentCrawler, availableThreads, workingThreads);
			Thread thread = new Thread(analyzer);
			ThreadConnection<Analyzer> threadConnection = new ThreadConnection<>(thread, analyzer);
			try {
				availableThreads.put(threadConnection);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//TODO
			System.out.println("Starting analyzer: " + i);
			thread.start();
		}
	}

	@Override
	public void run() {
		while (isActive) {
			
			synchronized (HtmlsToAnalyze) {
				while (!HtmlsToAnalyze.isEmpty()) {
					activateAnalyzer();	
				}
				
				try {
					HtmlsToAnalyze.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		}
		
	}
	
	private void activateAnalyzer() {
		if (!HtmlsToAnalyze.isEmpty()) {
			try {
				HTMLContent url = HtmlsToAnalyze.take();
				ThreadConnection<Analyzer> workerThread = availableThreads.take();
				workingThreads.add(workerThread);
				System.out.println("Sending analyzer to work");
				workerThread.threadedClass.SetHTML(url, workerThread);
				
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		
	}
	
}
