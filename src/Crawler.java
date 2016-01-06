import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Crawler {
	private static final int MAX_PAGES_TO_SEARCH = 100;
	private static String maxDownloadersKey = "maxDownloaders";
	private static String maxAnalyzersKey = "maxAnalyzers";
	private static String imageExtensionsKey = "imageExtensions";
	private static String videoExtensionsKey = "videoExtensions";
	private static String documentExtensionsKey = "documentExtensions";
	private static int maxDownloaders;
	private static int maxAnalyzers;
	private static List<String> imageExtensionsList;
	private static List<String> videoExtensionsList;
	private static List<String> documentExtensionsList;
	private Set<String> pagesVisited = new HashSet<String>();
	private List<String> pagesToVisit = new LinkedList<String>();
	private Set<String> forbiddenPages = new HashSet<String>();
	private int numberOfImages = 0;
	private int totalSizeOfImages = 0;
	private int numberOfVideos = 0;
	private int totalSizeOfVideos = 0;
	private int numberOfDocuments = 0;
	private int totalSizeOfDocuments = 0;
	private int numberOfPages = 0;
	private int totalSizeOfPages = 0;
	// Links from the same domain
	private int numberOfInternalLinks = 0;
	private int numberOfExternalLinks = 0;



	public Crawler(HashMap<String, String> crawlerConfiguration) {
		
		System.out.println(crawlerConfiguration.get(maxDownloadersKey));
		
		maxDownloaders = Integer.parseInt(crawlerConfiguration.get(maxDownloadersKey));
		maxAnalyzers = Integer.parseInt(crawlerConfiguration.get(maxAnalyzersKey));
		imageExtensionsList = stringToList(crawlerConfiguration.get(imageExtensionsKey));
		videoExtensionsList = stringToList(crawlerConfiguration.get(videoExtensionsKey));
		documentExtensionsList = stringToList(crawlerConfiguration.get(documentExtensionsKey));
		
		System.out.println("Crawler created with configuration:");
		System.out.println("Downloader: " + maxDownloaders);
		System.out.println("Analyzers: " + maxAnalyzers);
		System.out.println("Image Extensions: " + Arrays.toString(imageExtensionsList.toArray()));
		System.out.println("Video Extensions: " + Arrays.toString(videoExtensionsList.toArray()));
		System.out.println("Document Extensions: " + Arrays.toString(documentExtensionsList.toArray()));
	}


	private List<String> stringToList(String listAsString) {
		
		List<String> result = new LinkedList<String>();
		String[] seperatedString = listAsString.split(",");
		for (int i = 0; i < seperatedString.length; i++) {
			String cleanString = seperatedString[i].trim().toLowerCase();
			if (!cleanString.isEmpty()) {
				result.add(cleanString);	
			}
		}
		return result;
	}


	public void search(String url)
	{
		while(this.pagesVisited.size() < MAX_PAGES_TO_SEARCH)
		{
			String currentUrl;
			CrawlerLeg leg = new CrawlerLeg();
			if(this.pagesToVisit.isEmpty())
			{
				currentUrl = url;
				this.pagesVisited.add(url);
			}
			else
			{
				currentUrl = this.nextUrl();
			}
			boolean success = leg.crawl(currentUrl); // Lots of stuff happening here. Look at the crawl method in
			// CrawlerLeg

			if(success)
			{
				System.out.println(String.format("**Success** Crawling %s has finished.", currentUrl));
				this.pagesToVisit.addAll(leg.getLinks());
				updateStatistics(leg);
			}else{
				System.out.println(String.format("**Failure** Crawling %s has not finished.", currentUrl));
			}

		}
		System.out.println("\n**Done** Visited " + this.pagesVisited.size() + " web page(s)");
	}


	/**
	 * Returns the next URL to visit (in the order that they were found). We also do a check to make
	 * sure this method doesn't return a URL that has already been visited.
	 * 
	 * @return
	 */
	private String nextUrl()
	{
		String nextUrl;
		do
		{
			nextUrl = this.pagesToVisit.remove(0);
		} while(this.pagesVisited.contains(nextUrl));
		this.pagesVisited.add(nextUrl);
		return nextUrl;
	}

	public void updateStatistics(CrawlerLeg leg){
		updateImages(leg);
		updateVideos(leg);
		updateDocuments(leg);
		updateImages(leg);
	}

	public void updateImages(CrawlerLeg leg){
		this.numberOfImages += leg.getNumberOfImages();
		this.totalSizeOfImages += leg.getTotalSizeOfImages();
	}

	public void updateVideos(CrawlerLeg leg){
		this.numberOfVideos += leg.getNumberOfVideos();
		this.totalSizeOfVideos += leg.getTotalSizeOfVideos();
	}

	public void updateDocuments(CrawlerLeg leg){
		this.numberOfDocuments += leg.getNumberOfDocuments();
		this.totalSizeOfDocuments += leg.getTotalSizeOfDocuments();
	}

	public void updatePages(CrawlerLeg leg){
		this.numberOfPages += leg.getNumberOfPages();
		this.totalSizeOfPages += leg.getTotalSizeOfPages();
	}
}
