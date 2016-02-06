
public class DownloadersManager implements Runnable{

	private Crawler parentCrawler;
	private int maxDownloaders;
	private Boolean isActive;
	private SynchronizedQueue<String> urlsToDownload;
	private SynchronizedQueue<ThreadConnection<Downloader>> availableThreads;
	private SynchronizedSet<ThreadConnection<Downloader>> workingThreads;

	public DownloadersManager(int maxThreads, Crawler crawler, SynchronizedQueue<String> urlsToDownload) {

		parentCrawler = crawler;
		maxDownloaders = maxThreads;
		this.urlsToDownload = urlsToDownload;
		isActive = true;

		availableThreads = new SynchronizedQueue<>();
		workingThreads = new SynchronizedSet<>();

		for (int i = 0; i < maxDownloaders; i++) {
			Downloader downloader = new Downloader(parentCrawler, availableThreads, workingThreads);
			Thread thread = new Thread(downloader);
			ThreadConnection<Downloader> threadConnection = new ThreadConnection<>(thread, downloader);
			try {
				availableThreads.put(threadConnection);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			thread.start();
		}
	}

	@Override
	public void run() {
		while (isActive) {

			synchronized (urlsToDownload) {
				while (!urlsToDownload.isEmpty()) {
					activateDownloader();	
				}

				try {
					urlsToDownload.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}

	}

	private void activateDownloader() {
		
		try {
			String url = urlsToDownload.take();
			System.out.println("DownloaderManager: Taking an available thread for url: " + url);
			ThreadConnection<Downloader> workerThread = availableThreads.take();
			System.out.println("DownloaderManager: Took an available thread.");
			workingThreads.add(workerThread);
			System.out.println("DownloaderManager: Added worker thread to working.");
			workerThread.threadedClass.setURL(url, workerThread);
			


		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
