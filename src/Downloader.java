import java.io.IOException;
import java.util.List;


public class Downloader implements Runnable {
	
	Crawler parentCrawler;
	
	public Downloader(Crawler crawler) {
		parentCrawler = crawler;
	}

	@Override
	public void run() {
		
		String urlToDownload;
		String fileType;
		String requestType;
		List<String> documentExtensions = parentCrawler.getDocumentExtensions();
		List<String> imageExtensions = parentCrawler.getImageExtensions();
		List<String> videoExtensions = parentCrawler.getVideoExtensions();
		while (true) {
			ClientRequest clientRequest = null;
			urlToDownload = null;
			// Get url from parent
			try {
				if (parentCrawler.pagesVisited.size() < Crawler.MAX_PAGES_TO_SEARCH) {
					urlToDownload = parentCrawler.nextUrlToDownload();	
					parentCrawler.AdjustWorkingThreadCount(1);
				} else {
					return;
				}
				

			} catch (InterruptedException e1) {
				System.out.println("Error getting url, continuing to next.");
				e1.printStackTrace();
				continue;
			}
			if (urlToDownload == null) {
				System.out.println("URL TO DOWNLOAD WAS NULL");
				parentCrawler.AdjustWorkingThreadCount(-1);
				return;
			}
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
				System.out.println("Error getting " + urlToDownload);
				e.printStackTrace();
				continue;
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
						parentCrawler.addHtmlToAnalyze(clientRequest.getBody(), clientRequest.host);
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
			
			parentCrawler.AdjustWorkingThreadCount(-1);
			
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
