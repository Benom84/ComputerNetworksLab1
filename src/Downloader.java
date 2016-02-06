import java.io.IOException;
import java.util.List;


public class Downloader implements Runnable {

	private Crawler parentCrawler;
	private Boolean running;
	private List<String> documentExtensions;
	private List<String> imageExtensions;
	private List<String> videoExtensions;
	private String urlToDownload = "";
	private ThreadConnection<Downloader> threadedConnection;
	private SynchronizedQueue<ThreadConnection<Downloader>> availableQueue;
	private SynchronizedSet<ThreadConnection<Downloader>> workingSet;

	public Downloader(Crawler crawler, SynchronizedQueue<ThreadConnection<Downloader>> availableQueue, SynchronizedSet<ThreadConnection<Downloader>> workingSet) {
		parentCrawler = crawler;
		this.availableQueue = availableQueue;
		this.workingSet = workingSet;
	}

	@Override
	public void run() {

		documentExtensions = parentCrawler.getDocumentExtensions();
		imageExtensions = parentCrawler.getImageExtensions();
		videoExtensions = parentCrawler.getVideoExtensions();


		running = true;
		while (running) {


			System.out.println("Downloader: urlToDownload is: " + urlToDownload);
			if (!urlToDownload.equals("")) {
				System.out.println("Downloader: Activating download for: " + urlToDownload);
				downloadURL();
			}
			System.out.println("Downloader: going to sleep.");
			synchronized(urlToDownload) {
				try {

					while (urlToDownload.equals(""))
						urlToDownload.wait(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("Downloader: Woken up from wait.");
		}
	}

	public void setURL(String urlToDownload, ThreadConnection<Downloader> threadedConnection) {
		System.out.println("Donwloader: setURL was activated, entering synchronized.");
		synchronized(urlToDownload) {
			this.threadedConnection = threadedConnection;
			this.urlToDownload = urlToDownload;
			System.out.println("Downloader: Received setURL. Setting urlToDownload to: " + urlToDownload);
			urlToDownload.notifyAll();
			System.out.println("Downloader: Received setURL. notified all.");
		}


	}

	private void downloadURL() {

		String fileType;
		String requestType;
		ClientRequest clientRequest = null;
		System.out.println("Downloader: downloadURL: got url: " + urlToDownload);
		fileType = getFileTypeFromURL(urlToDownload);
		System.out.println("File type is: " + fileType);
		if (fileType.isEmpty() || fileType.equalsIgnoreCase("html") || fileType.equalsIgnoreCase("htm")) {
			System.out.println("Creating client request of type get");
			requestType = ClientRequest.getRequest;
		} else {
			System.out.println("Creating client request of type head");
			requestType = ClientRequest.headRequest;
		}
		try {
			clientRequest = new ClientRequest(urlToDownload, requestType);

		} catch (IOException e) {
			System.out.println("Error in client request to: " + urlToDownload);
			e.printStackTrace();
			return;
		}

		// Check the response
		String response = clientRequest.getResponseStatusCode(); 
		if (response.equals("200")) {
			System.out.println("In response header:");
			for (String string : clientRequest.responseHeaderFields.keySet()) {
				System.out.println("Key: " + string + " Value: " +  clientRequest.responseHeaderFields.get(string));
			}
			System.out.println("Content-Length: " + clientRequest.responseHeaderFields.get("Content-Length"));
			int sizeOfFile = 0; 
			if (clientRequest.responseHeaderFields.containsKey(("Content-Length"))) {
				sizeOfFile = Integer.parseInt(clientRequest.responseHeaderFields.get("Content-Length"));	
			}


			// If it was an HTML Page
			if (requestType.trim().equalsIgnoreCase("get")) {
				try {
					System.out.println("************************************Adding body to analyze**************************************");
					parentCrawler.addHtmlToAnalyze(clientRequest.getBody(), clientRequest.host);
					System.out.println("************************************Added body to analyze**************************************");
					if (sizeOfFile == 0) {
						sizeOfFile = clientRequest.getBody().length();
					}
					parentCrawler.updatePages(1, sizeOfFile);
					parentCrawler.UpdatePagesVisited(urlToDownload);
				} catch (InterruptedException e) {
					System.out.println("Error add html body for " + urlToDownload);
					e.printStackTrace();
				}
			} else {

				if (documentExtensions.contains(fileType)) {
					parentCrawler.updateDocuments(1, sizeOfFile);
				} else if (imageExtensions.contains(fileType)) {
					parentCrawler.updateImages(1, sizeOfFile);
				} else if (videoExtensions.contains(fileType)) {
					parentCrawler.updateVideos(1, sizeOfFile);
				}
			}

		} else if (response.equals("302") || response.equals("301")) {
			String newURL = clientRequest.responseHeaderFields.get("Location");
			System.out.println("The request returned moved to location: " + newURL);
			try {
				boolean isURLFullHTTPResult = isURLFullHTTP(newURL); 
				boolean isUrlAdded = false;
				if (isURLFullHTTPResult) {
					isUrlAdded = parentCrawler.addUrlToDownload(newURL);
				} else {
					isUrlAdded = parentCrawler.addUrlToDownload(clientRequest.host + newURL);
				}

				System.out.println(newURL + " added to download queue? " + isUrlAdded);
			} catch (InterruptedException e) {
				System.out.println("Error adding new url after 302: " + newURL);
				e.printStackTrace();
			}
		}

		urlToDownload = "";
		try {
			System.out.println("Downloader finished: adding itself to available");
			availableQueue.put(threadedConnection);
			System.out.println("Downloader finished: removing itself from working");
			workingSet.remove(threadedConnection);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private boolean isURLFullHTTP(String newURL) {

		String startOfURL = newURL.substring(0, 4);
		System.out.println(startOfURL);
		return newURL.substring(0, 4).equalsIgnoreCase("http");
	}

	private String getFileTypeFromURL(String urlToDownload) {

		System.out.println("Url: " + urlToDownload);
		if (urlToDownload.lastIndexOf('/') == -1)
			return "";
		String fileName = urlToDownload.substring( urlToDownload.lastIndexOf('/')+1, urlToDownload.length() );
		System.out.println("File Name " + fileName);
		String fileExtension = "";
		if (!fileName.isEmpty()) {
			fileExtension= fileName.substring(fileName.lastIndexOf('.') + 1, fileName.length());
			fileExtension = fileExtension.trim();	
		}

		return fileExtension;
	}
}
