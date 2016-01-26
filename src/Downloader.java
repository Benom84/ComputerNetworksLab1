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
			// Get url from parent
			try {
				urlToDownload = parentCrawler.nextUrlToDownload();

			} catch (InterruptedException e1) {
				System.out.println("Error getting url, continuing to next.");
				e1.printStackTrace();
				continue;
			}
			fileType = getFileTypeFromURL(urlToDownload);
			if (fileType.isEmpty() || fileType.equalsIgnoreCase("html") || fileType.equalsIgnoreCase("htm")) {
				requestType = ClientRequest.getRequest;
			} else {
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
				int sizeOfFile = Integer.parseInt(clientRequest.responseHeaderFields.get("Content-Length"));

				// If it was an HTML Page
				if (requestType.trim().equalsIgnoreCase("get")) {
					try {
						parentCrawler.addHtmlToAnalyze(clientRequest.getBody());
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
				try {
					parentCrawler.addUrlToDownload(newURL);
				} catch (InterruptedException e) {
					System.out.println("Error adding new url after 302: " + newURL);
					e.printStackTrace();
				}
			}
			
		}

		
		
		
	}

	private String getFileTypeFromURL(String urlToDownload) {

		String fileName = urlToDownload.substring( urlToDownload.lastIndexOf('/')+1, urlToDownload.length() );
		String fileExtension = fileName.substring(fileName.lastIndexOf('.'), fileName.length());
		fileExtension = fileExtension.trim();
		return fileExtension;
	}
}
