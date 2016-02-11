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
				urlToDownload = parentCrawler.nextUrlToDownload();
			} catch (InterruptedException e1) {
				//e1.printStackTrace();
			}

			if (!running) {
				System.out.println("Downloader is shutting down.");
				return;
			}

			if (!(urlToDownload == null)) {
				System.out.println("Downloader: Starting downloading file: " + urlToDownload);
				downloadURL();
				parentCrawler.lowerWorkload();
				System.out.println("Downloader: Finished downloading file: " + urlToDownload);
			}
		}
	}

	private void downloadURL() {

		String fileType;
		String requestType;
		ClientRequest clientRequest = null;
		fileType = getFileTypeFromURL(urlToDownload);
		if (fileType.isEmpty() || fileType.equalsIgnoreCase("html") || fileType.equalsIgnoreCase("htm")) {
			requestType = ClientRequest.getRequest;
		} else {
			requestType = ClientRequest.headRequest;
		}
		try {
			clientRequest = new ClientRequest(urlToDownload, requestType);

		} catch (Exception e) {
			System.out.println("Error in client request to: " + urlToDownload);
			return;
		}

		// Update the RTT statistics
		parentCrawler.addRTT(clientRequest.getRTTtime());

		// Check the response
		String response = clientRequest.getResponseStatusCode(); 
		if (response.equals("200")) {

			int sizeOfFile = 0; 
			if (clientRequest.responseHeaderFields.containsKey(("Content-Length"))) {
				sizeOfFile = Integer.parseInt(clientRequest.responseHeaderFields.get("Content-Length"));	
			}

			// If it was an HTML Page
			if (requestType.trim().equalsIgnoreCase("get")) {
				try {
					parentCrawler.addHtmlToAnalyze(clientRequest.getBody(), clientRequest.host, urlToDownload);
					if (sizeOfFile == 0) {
						sizeOfFile = clientRequest.getBody().length();
					}
					parentCrawler.updatePages(1, sizeOfFile);
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
			try {
				boolean isURLFullHTTPResult = isURLFullHTTP(newURL); 
				if (isURLFullHTTPResult) {
					parentCrawler.addUrlToDownload(newURL);
				} else {
					if (!newURL.startsWith("/")) {
						newURL = "/" + newURL;
					}
					parentCrawler.addUrlToDownload(clientRequest.host + newURL);
				}

			} catch (InterruptedException e) {
				System.out.println("Error adding new url after 302: " + newURL);
				e.printStackTrace();
			}
		}
		urlToDownload = "";
	}

	private boolean isURLFullHTTP(String newURL) {

		if (newURL.length() > 7)
			return newURL.substring(0, 7).equalsIgnoreCase("http://");
		else
			return false;
	}

	private String getFileTypeFromURL(String urlToDownload) {

		Matcher domainMatcher = Crawler.DOMAIN_PATTERN.matcher(urlToDownload);
		String fileExtension = "";
		if (domainMatcher.find()) {
			if (domainMatcher.group(1) == null && domainMatcher.group(2).endsWith(":")) {
				String url = urlToDownload + "/";
				getFileTypeFromURL(url);
			}
			else if (domainMatcher.group(3) != null) {
				String link = domainMatcher.group(3);
				if (link.lastIndexOf('/') == -1)
					return "";
				String fileName = link.substring( link.lastIndexOf('/')+1, link.length() );
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
