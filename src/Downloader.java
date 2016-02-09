import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;


public class Downloader implements Runnable {

	private Crawler parentCrawler;
	private Boolean running;
	private List<String> documentExtensions;
	private List<String> imageExtensions;
	private List<String> videoExtensions;
	private String urlToDownload = "";

	public Downloader(Crawler crawler) {
		parentCrawler = crawler;
	}

	@Override
	public void run() {

		documentExtensions = parentCrawler.getDocumentExtensions();
		imageExtensions = parentCrawler.getImageExtensions();
		videoExtensions = parentCrawler.getVideoExtensions();


		running = true;
		while (running) {


			try {
				System.out.println("Downloader: Requesting next url to download");
				urlToDownload = parentCrawler.nextUrlToDownload();
			} catch (InterruptedException e1) {
				//e1.printStackTrace();
			}
			
			if (!running) {
				System.out.println("Downloader is shutting down.");
				return;
			}
			
			System.out.println("Downloader: urlToDownload is: " + urlToDownload);
			if (!(urlToDownload == null)) {
				System.out.println("Downloader: Activating download for: " + urlToDownload);
				downloadURL();
				//TODO delete temp
				int temp = parentCrawler.lowerWorkload();
				//System.out.println("#################Downloader finished downloading and workload is now: " + temp);
			}
		}
	}

	private void downloadURL() {

		String fileType;
		String requestType;
		ClientRequest clientRequest = null;
		//System.out.println("Downloader: downloadURL: got url: " + urlToDownload);
		fileType = getFileTypeFromURL(urlToDownload);
		//System.out.println("File type is: " + fileType);
		if (fileType.isEmpty() || fileType.equalsIgnoreCase("html") || fileType.equalsIgnoreCase("htm")) {
			//System.out.println("Creating client request of type get");
			requestType = ClientRequest.getRequest;
		} else {
			//System.out.println("Creating client request of type head");
			requestType = ClientRequest.headRequest;
		}
		try {
			clientRequest = new ClientRequest(urlToDownload, requestType);

		} catch (IOException e) {
			System.out.println("Error in client request to: " + urlToDownload);
			e.printStackTrace();
			return;
		}

		// Update the RTT statistics
		parentCrawler.addRTT(clientRequest.getRTTtime());
		
		// Check the response
		String response = clientRequest.getResponseStatusCode(); 
		if (response.equals("200")) {
			//System.out.println("In response header:");
			/*for (String string : clientRequest.responseHeaderFields.keySet()) {
				System.out.println("Key1: " + string + " Value: " +  clientRequest.responseHeaderFields.get(string));
			}*/
			//System.out.println("Content-Length: " + clientRequest.responseHeaderFields.get("Content-Length"));
			
			int sizeOfFile = 0; 
			if (clientRequest.responseHeaderFields.containsKey(("Content-Length"))) {
				sizeOfFile = Integer.parseInt(clientRequest.responseHeaderFields.get("Content-Length"));	
			}

			//System.out.println("Downloader: Handling body");

			// If it was an HTML Page
			if (requestType.trim().equalsIgnoreCase("get")) {
				try {
					//System.out.println("************************************Adding body to analyze**************************************");
					parentCrawler.addHtmlToAnalyze(clientRequest.getBody(), clientRequest.host);
					//System.out.println("************************************Added body to analyze**************************************");
					if (sizeOfFile == 0) {
						sizeOfFile = clientRequest.getBody().length();
					}
					parentCrawler.updatePages(1, sizeOfFile);
					//TODO parentCrawler.UpdatePagesVisited(urlToDownload);
				} catch (InterruptedException e) {
					System.out.println("Error add html body for " + urlToDownload);
					e.printStackTrace();
				}
			} else {

				System.out.println("Downloader: It was a head request. Updating statistcs for file type: " + fileType);
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
			//System.out.println("The request returned moved to location: " + newURL);
			try {
				boolean isURLFullHTTPResult = isURLFullHTTP(newURL); 
				boolean isUrlAdded = false;
				if (isURLFullHTTPResult) {
					isUrlAdded = parentCrawler.addUrlToDownload(newURL);
				} else {
					isUrlAdded = parentCrawler.addUrlToDownload(clientRequest.host + newURL);
				}

				//System.out.println(newURL + " added to download queue? " + isUrlAdded);
			} catch (InterruptedException e) {
				System.out.println("Error adding new url after 302: " + newURL);
				e.printStackTrace();
			}
		}
		//System.out.println("Downloader: Finished downloading");
		urlToDownload = "";
	}

	private boolean isURLFullHTTP(String newURL) {

		if (newURL.length() > 7)
			return newURL.substring(0, 7).equalsIgnoreCase("http://");
		else
			return false;
	}

	private String getFileTypeFromURL(String urlToDownload) {

		//System.out.println("Downloader: getFileTypeFromURL: Url: " + urlToDownload);
		Matcher domainMatcher = Crawler.DOMAIN_PATTERN.matcher(urlToDownload);
		String fileExtension = "";
		if (domainMatcher.find()) {
			//System.out.println("Downloader: getFileTypeFromURL: Found Match");
			if (domainMatcher.group(1) == null && domainMatcher.group(2).endsWith(":")) {
				System.out.println("Downloader: getFileTypeFromURL: is the url to recheck is: " + urlToDownload);
				String url = urlToDownload + "/";
				getFileTypeFromURL(url);
			}
			else if (domainMatcher.group(3) != null) {
				//System.out.println("Downloader: getFileTypeFromURL: domainMatcher found for group 3: " + domainMatcher.group(3));
				String link = domainMatcher.group(3);
				if (link.lastIndexOf('/') == -1)
					return "";
				String fileName = link.substring( link.lastIndexOf('/')+1, link.length() );
				//System.out.println("File Name " + fileName);
				if (!fileName.isEmpty()) {
					if (fileName.lastIndexOf('.') > 0) {
						fileExtension= fileName.substring(fileName.lastIndexOf('.') + 1, fileName.length());
						fileExtension = fileExtension.trim().toLowerCase();		
					}
					
				}
			}
			
		}
		

		return fileExtension;
	}
	
	public void shutdown() {
		running = false;
	}
}
