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
	private Set<String> pagesVisited;
	private SynchronizedQueue<String> urlsToDownload;
	private SynchronizedQueue<String> htmlToAnalyze;
	private Set<String> forbiddenPages;
	private TypeStatistics imageFiles;
	private TypeStatistics videoFiles;
	private TypeStatistics documentFiles;
	private TypeStatistics pagesFiles;
	// Links from the same domain
	private int numberOfInternalLinks;
	private int numberOfExternalLinks;
	private Thread[] downloaderThreads;
	private Thread[] analyzerThreads;
	private Boolean isCrawlerRunning = false;
	private String portScanResults;



	public Crawler(HashMap<String, String> crawlerConfiguration) {

		System.out.println(crawlerConfiguration.get(maxDownloadersKey));

		maxDownloaders = Integer.parseInt(crawlerConfiguration.get(maxDownloadersKey));
		maxAnalyzers = Integer.parseInt(crawlerConfiguration.get(maxAnalyzersKey));
		imageExtensionsList = stringToList(crawlerConfiguration.get(imageExtensionsKey));
		videoExtensionsList = stringToList(crawlerConfiguration.get(videoExtensionsKey));
		documentExtensionsList = stringToList(crawlerConfiguration.get(documentExtensionsKey));
		imageFiles = new TypeStatistics();
		videoFiles = new TypeStatistics();
		documentFiles = new TypeStatistics();
		pagesFiles = new TypeStatistics();

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

	public boolean isBusy() {
		return isCrawlerRunning;
	}

	public String activateCrawler(String targetURL, boolean ignoreRobots, boolean performPortScan) throws InterruptedException {

		String result;
		if (isCrawlerRunning) {
			return "Crawler already running";
		} else {
			synchronized (isCrawlerRunning) {
				if (isCrawlerRunning) {
					return "Crawler already running"; 
				}
				initStatistics();
				isCrawlerRunning = true;
				if (performPortScan) {
					portScanResults = portScan(targetURL);
				} else {
					portScanResults = "Port scan was not selected.";
				}
				
				Set<String> pagesFromRobotsFile = readRobotsFile(targetURL);
				if (pagesFromRobotsFile != null) {
					if (ignoreRobots) {
						for (String page : pagesFromRobotsFile) {
							urlsToDownload.put(page);
						}
					} else {
						forbiddenPages = pagesFromRobotsFile;
					}
				} else {
					forbiddenPages = new HashSet<String>();
				}
				
				
				downloaderThreads = new Thread[maxDownloaders - 1];
				analyzerThreads = new Thread[maxAnalyzers - 1];
				for (int i = 0; i < analyzerThreads.length; i++) {
					analyzerThreads[i] = new Thread(new Analyzer(this));
				}
				for (int i = 0; i < downloaderThreads.length; i++) {
					downloaderThreads[i] = new Thread(new Downloader(this));
				}
				result = "Activated crawler";	
			}

		}

		return result;
	}

	private Set<String> readRobotsFile(String targetURL) {
		// TODO Read robots file from request url and put in set
		return null;
	}


	private String portScan(String targetURL) {
		// TODO perform port scan
		return "Implement me??";
	}


	private void initStatistics() {

		pagesVisited = new HashSet<String>();
		urlsToDownload = new SynchronizedQueue<String>();
		htmlToAnalyze = new SynchronizedQueue<String>();
		numberOfInternalLinks = 0;
		numberOfExternalLinks = 0;
		imageFiles.init();
		videoFiles.init();
		documentFiles.init();
		pagesFiles.init();

	}

	public void search(String url) throws InterruptedException
	{
		while(this.pagesVisited.size() < MAX_PAGES_TO_SEARCH)
		{
			String currentUrl;
			CrawlerLeg leg = new CrawlerLeg();
			if(this.urlsToDownload.isEmpty())
			{
				currentUrl = url;
				this.pagesVisited.add(url);
			}
			else
			{
				currentUrl = this.nextUrlToDownload();
			}
			boolean success = leg.crawl(currentUrl); // Lots of stuff happening here. Look at the crawl method in
			// CrawlerLeg

			if(success)
			{
				System.out.println(String.format("**Success** Crawling %s has finished.", currentUrl));
				this.urlsToDownload.addAll(leg.getLinks());
				//updateStatistics(leg);
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
	 * @throws InterruptedException 
	 */
	protected String nextUrlToDownload() throws InterruptedException
	{
		String nextUrl;
		do
		{
			nextUrl = this.urlsToDownload.take();
		} while(this.pagesVisited.contains(nextUrl) || this.forbiddenPages.contains(nextUrl));
		this.pagesVisited.add(nextUrl);
		return nextUrl;
	}

	protected void addUrlToDownload(String url) throws InterruptedException {

		if (!pagesVisited.contains(url)) {
			urlsToDownload.put(url);
		}	
	}

	protected String nextHtmlToAnalyze() throws InterruptedException {
		return htmlToAnalyze.take();
	}
	
	protected void addHtmlToAnalyze() throws InterruptedException {
		htmlToAnalyze.take();
	}

	protected void updateImages(int numberOfImages, int sizeOfImages){

		synchronized (imageFiles) {
			imageFiles.updateCounter(numberOfImages, sizeOfImages);
		}
	}

	protected void updateVideos(int numberOfVideos, int sizeOfVideos){

		synchronized (videoFiles) {
			videoFiles.updateCounter(numberOfVideos, sizeOfVideos);
		}
	}

	protected void updateDocuments(int numberOfDocuments, int sizeOfDocuments){
		synchronized (documentFiles) {
			documentFiles.updateCounter(numberOfDocuments, sizeOfDocuments);
		}
	}

	protected void updatePages(int numberOfPages, int sizeOfPages){
		synchronized (pagesFiles) {
			pagesFiles.updateCounter(numberOfPages, sizeOfPages);
		}
	}

	public List<String> getImageExtensions() {
		return cloneList(imageExtensionsList);
	}

	public List<String> getVideoExtensions() {
		return cloneList(videoExtensionsList);
	}

	public List<String> getDocumentExtensions() {
		return cloneList(documentExtensionsList);
	}


	private List<String> cloneList(List<String> list) {
		List<String> result = new LinkedList<String>();
		for (String string : list) {
			result.add(string);
		}
		return result;
	}
}
