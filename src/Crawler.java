

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler implements Runnable {
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
	protected static final int MAX_PAGES_TO_SEARCH = 100;
	private static final int MAX_PORTNUMBER_TO_SCAN = 1024;
	private static String maxDownloadersKey = "maxDownloaders";
	private static String maxAnalyzersKey = "maxAnalyzers";
	private static String imageExtensionsKey = "imageExtensions";
	private static String videoExtensionsKey = "videoExtensions";
	private static String documentExtensionsKey = "documentExtensions";
	private static String rootKey = "root";
	private static int maxDownloaders;
	private static int maxAnalyzers;
	private static List<String> imageExtensionsList;
	private static List<String> videoExtensionsList;
	private static List<String> documentExtensionsList;
	protected Set<String> crawledDomains;
	protected SynchronizedSet<String> pagesVisited;
	protected SynchronizedSet<String> externalDomains;
	private SynchronizedSet<String> internalLinks;
	private SynchronizedSet<String> externalLinks;
	private SynchronizedQueue<String> urlsToDownload;
	private SynchronizedQueue<HTMLContent> htmlToAnalyze;
	private SynchronizedSet<String> forbiddenPages;
	SynchronizedSet<Integer> workingDownloaderThreadNumbers;
	SynchronizedSet<Integer> workingAnalyzerThreadNumbers; 
	private TypeStatistics imageFiles;
	private TypeStatistics videoFiles;
	private TypeStatistics documentFiles;
	private TypeStatistics pagesFiles;
	protected String targetURL;
	private boolean ignoreRobots;
	private boolean performPortScan;
	// Links from the same domain
	private Boolean isCrawlerRunning = false;
	private String portScanResults;
	private String allowedHost;
	private Integer workingThreads;
	private String startDate;
	private String startTime;
	private String rootDir;

	private int requestCount;
	private Float sumOfRTT;
	private static final String resultsFolder = "\\ScanResults\\";
	private Object workingThreadsKey;
	//private static final Pattern urlPattern = Pattern.compile(".*?(http:\\/\\/|https:\\/\\/)?(www.)?(.*?)(\\/.*)$");
	private static final Pattern urlPattern = Pattern.compile("((^[Hh][Tt][Tt][Pp][Ss]?):\\/\\/)?((www.)?(.*))");


	public static void main(String[] args) throws IOException {
		//Crawler aviv = new Crawler();
		//Set<String> testing = aviv.readRobotsFile("http://www.google.com/robots.txt");
		//ClientRequest testing = new ClientRequest("ynet.co.il", ClientRequest.headRequest);
		//System.out.println(testing.getResponseStatusCode());
		StringBuilder SB = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\AvivPC\\Desktop\\ForCrawler\\linksExtracted - Ynet3.txt"));
		String line = br.readLine();
		while(line != null){
			if(!line.startsWith("Excluded:")) {
				System.out.println("Parse for url: " + line);
				pasrseURL(line);
				System.out.println(System.lineSeparator());
			}
			line = br.readLine();
		}
	}

	public Crawler(HashMap<String, String> crawlerConfiguration) {

		rootDir = crawlerConfiguration.get(rootKey);
		System.out.println("ROOT DIR IS: " + rootDir);
		createResultsFolder();
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

	private void createResultsFolder() {
		File resultsFolderFile = new File(rootDir + resultsFolder);
		if (!resultsFolderFile.exists()) {
			resultsFolderFile.mkdirs();
		}
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

	public boolean ConfigureCrawler(String targetURL, boolean ignoreRobots, boolean performPortScan) {
		if (isCrawlerRunning) {
			return false;
		}

		this.targetURL = pasrseURL(targetURL);

		if (this.targetURL.charAt(this.targetURL.length() - 1) == '\\') {
			this.targetURL = this.targetURL.substring(0, this.targetURL.length() - 1);
		}
		this.ignoreRobots = ignoreRobots;
		this.performPortScan = performPortScan;
		this.externalDomains = new SynchronizedSet<>();
		return true;
	}

	private static String pasrseURL(String url) {
		String parsedURL = "";

		try {
			Matcher matcher = urlPattern.matcher(url);
			if (matcher.find()) {

				parsedURL = matcher.group(3);
				int indexOfFirstSlash = parsedURL.indexOf('/');
				if (indexOfFirstSlash > 0) {
					parsedURL = parsedURL.substring(0, indexOfFirstSlash);
				}
				System.out.println("Host is: " + parsedURL);

			}

		} catch(Exception e){
			System.out.println("Failed to parse the Url: " + url);
		}

		return parsedURL;
	}

	public boolean isBusy() {
		return isCrawlerRunning;
	}

	public void addRTT(float currentRTT) {
		synchronized (sumOfRTT) {
			sumOfRTT += currentRTT;
			requestCount++;
		}
	}

	public void run() {

		if (isCrawlerRunning) {
			return;
		} else {
			synchronized (isCrawlerRunning) {
				if (isCrawlerRunning) {
					return;
				}
				initStatistics();
				readCrawledDomains();
				isCrawlerRunning = true;
				System.out.println("************* Started Crawler *****************");
				DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd");
				DateFormat timeFormat = new SimpleDateFormat("HH_mm_ss");
				Date time = new Date(new Date().getTime());
				startTime = timeFormat.format(time);
				startDate = dateFormat.format(time);

				System.out.println("Start Time: " + startTime + " Start Date: " + startDate);

			}
			if (performPortScan) {
				portScanResults = portScan(targetURL);
			} else {
				portScanResults = "Port scan was not selected.";
			}

			SynchronizedSet<String> pagesFromRobotsFile = null;
			try {
				pagesFromRobotsFile = readRobotsFile(targetURL);
			} catch (IOException e) {
				System.out.println("Error reading robots file from: " + targetURL);
				e.printStackTrace();
			}
			if (pagesFromRobotsFile != null) {
				if (ignoreRobots) {
					for (String page : pagesFromRobotsFile) {
						try {
							urlsToDownload.put(page);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				} else {
					forbiddenPages = pagesFromRobotsFile;
				}
			} else {
				forbiddenPages = new SynchronizedSet<String>();
			}

			try {
				urlsToDownload.put(targetURL);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}


			DownloadersManager downloadersManager = new DownloadersManager(maxDownloaders, this, urlsToDownload);
			AnalyzersManager analyzersManager = new AnalyzersManager(maxAnalyzers, this, htmlToAnalyze);
			Thread downloadersThread = new Thread(downloadersManager);
			Thread analyzersThread = new Thread(analyzersManager);
			downloadersThread.start();
			analyzersThread.start();

			// If (the threads are active or the queues are not empty) and we didn't exceed the pages to visit
			// TODO remove MAX_PAGES
			while (pagesVisited.size() < MAX_PAGES_TO_SEARCH) {
				
				synchronized (this) {
					try {
						wait(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
				}
				
			}



			System.out.println("All threads have joined");
			System.out.println("Creating results page");
			createResultPage();
			isCrawlerRunning = false;
		}
	}

	protected String NextUrlToDownload() {
		String result = "";
		synchronized (urlsToDownload) {
			try {
				result = urlsToDownload.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return result;
	}

	protected boolean IsHTMLToAnalyzeEmpty() {
		boolean result = false;
		synchronized (htmlToAnalyze) {
			result = htmlToAnalyze.isEmpty();
		}

		return result;
	}

	protected String NextHTMLToAnalyze() {
		String result = "";
		synchronized (urlsToDownload) {
			try {
				result = urlsToDownload.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return result;
	}


	private void readCrawledDomains() {
		crawledDomains = new HashSet<>();
		File resultsPath = new File(rootDir + resultsFolder);
		if (resultsPath.exists() && resultsPath.isDirectory()) {
			File[] resultsFiles = resultsPath.listFiles();
			for (File resultFile : resultsFiles) {
				String resultFileName = resultFile.getName();
				resultFileName = resultFileName.substring(0, resultFileName.length() - 25);
				System.out.println("Result File found for domain: " + resultFileName);
				crawledDomains.add(resultFileName);
			}
		}

	}

	private void createResultPage() {
		String resultPageName = targetURL + "_" + startDate + "_" + startTime + ".html";
		String resultPath = rootDir + resultsFolder + resultPageName;
		float averageRTT = sumOfRTT / requestCount;
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(resultPath), "utf-8"))) {
			writer.write("<!DOCTYPE html><html><head lang=\"en\"><meta charset=\"UTF-8\"><title>");
			writer.write(targetURL + " " + startDate + " " + startTime);
			writer.write("</title></head><body><h1>" + targetURL + " " + startDate + " " + startTime + "</h1><table>");
			writer.write("<tr><td>Was Robots file respected?</td><td>" + !ignoreRobots + "</td></tr>");
			writer.write("<tr><td>Category</td><td>Number</td><td>Size</td></tr>");
			writer.write("<tr><td>Images</td><td>" + imageFiles.getNumberOfFiles() + "</td><td>" + imageFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Videos</td><td>" + videoFiles.getNumberOfFiles() + "</td><td>" + videoFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Documents</td><td>" + documentFiles.getNumberOfFiles() + "</td><td>" + documentFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Pages</td><td>" + pagesFiles.getNumberOfFiles() + "</td><td>" + pagesFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("</table>");
			writer.write("<h2>Number of Internal Links: " + internalLinks + "</h2>");
			writer.write("<h2>Number of External Links: " + externalLinks + "</h2>");
			writer.write("<h2>Number of External Domains: " + externalDomains.size() + "</h2>");
			writer.write("<h2>The Domains connected:</h2>");
			writer.write("<ul>");
			for (String externalDomain : externalDomains) {
				if (crawledDomains.contains(externalDomain)) {
					writer.write("<li><a href=\""+ externalDomain + "\">" + externalDomain + "</a>" + externalDomain + "</li>");
				} else {
					writer.write("<li>" + externalDomain + "</li>");
				}

			}
			writer.write("</ul>");
			if (performPortScan) {
				writer.write("<h2>" + portScanResults + "</h2>");	
			}
			writer.write("<h2>Average RTT: " + averageRTT + "</h2>");
			writer.write("<h3><a href = \"/\">Back To Main Page</a></h3>");
			writer.write("</body></html>");


		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	protected void AdjustWorkingThreadCount(int num) {
		synchronized (workingThreads) {
			workingThreads = workingThreads + num;
		}
	}

	protected int GetWorkingThreadsCount() {
		int result;
		synchronized (workingThreads) {
			result = workingThreads;
		}
		return result;
	}

	private SynchronizedSet<String> readRobotsFile(String targetURL) throws IOException {

		SynchronizedSet<String> result = new SynchronizedSet<String>();
		ClientRequest connection = new ClientRequest(targetURL, ClientRequest.getRequest);
		//Connection connection = Jsoup.connect(targetURL).userAgent(USER_AGENT);
		//Document document = connection.get();
		//System.out.println("Debbug: Response code is " + connection.getResponseStatusCode());
		if (connection.getResponseStatusCode().equals("200")) {
			if (connection.getBody() != null) {
				String[] forbiddenUrls = connection.getBody().split(ClientRequest.CRLF);
				for (String url : forbiddenUrls) {
					if (url.contains("Disallow")) {
						int startOfSubStringIndex = url.indexOf(" ");
						String urlToForbiddenList = url.substring(startOfSubStringIndex + 1, url.length());
						System.out.println("Got the URL (From robots.txt): " + urlToForbiddenList);
						try {
							result.add(urlToForbiddenList);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}

			//System.out.println(forbiddenUrls.length);

		}
		return result;
	}


	private String portScan(String targetURL) {

		StringBuilder sb = new StringBuilder();
		sb.append("Open ports: ");
		System.out.println("Starting port scan on: " + targetURL);
		for (int port = 1; port <= MAX_PORTNUMBER_TO_SCAN; port++) {
			try {
				Socket socket = new Socket();
				socket.connect(new InetSocketAddress(targetURL, port), 100);
				sb.append(port + " ");
				System.out.println(port + ", ");
				socket.close();
			} catch (Exception e) {

			}

		}
		sb.deleteCharAt(sb.length() - 1);
		sb.deleteCharAt(sb.length() - 1);
		System.out.println("Ending port scan");
		return sb.toString();
	}


	private void initStatistics() {

		pagesVisited = new SynchronizedSet<String>();
		urlsToDownload = new SynchronizedQueue<String>();
		htmlToAnalyze = new SynchronizedQueue<HTMLContent>();
		internalLinks = new SynchronizedSet<>();
		externalLinks = new SynchronizedSet<>();
		sumOfRTT = 0f;
		requestCount = 0;
		imageFiles.init();
		videoFiles.init();
		documentFiles.init();
		pagesFiles.init();

	}

	public void search(String url) throws InterruptedException {
		allowedHost = getHost(url);
		if (allowedHost.equals("null")) {
			System.out.println("Host wasn't found");
			return;
		}
		while (this.pagesVisited.size() < MAX_PAGES_TO_SEARCH) {
			String currentUrl;
			CrawlerLeg leg = new CrawlerLeg();
			if (this.urlsToDownload.isEmpty()) {
				currentUrl = url;
				this.pagesVisited.add(url);
			} else {
				currentUrl = this.nextUrlToDownload();
			}
			boolean success = leg.crawl(this, currentUrl); // Lots of stuff happening here. Look at the crawl method in
			// CrawlerLeg

			if (success) {
				System.out.println(String.format("**Success** Crawling %s has finished.", currentUrl));
				this.urlsToDownload.addAll(leg.getLinks());
				//updateStatistics(leg);
			} else {
				System.out.println(String.format("**Failure** Crawling %s has not finished.", currentUrl));
			}

		}
		System.out.println("\n**Done** Visited " + this.pagesVisited.size() + " web page(s)");
	}

	private String getHost(String url) {
		//Group(1) is http:// or https://
		//Group(2) is www.
		//Group(3) is host
		//Group(4) is location
		try {
			Matcher matcher = urlPattern.matcher(url);
			if (matcher.find()) {

				return matcher.group(3);

			} else {
				if (!url.endsWith("/")) {
					getHost(url + "/");
				}
			}

		} catch (Exception e) {
			System.out.println("Failed to parse the Url: " + url);
		}
		return null;
	}


	/**
	 * Returns the next URL to visit (in the order that they were found). We also do a check to make
	 * sure this method doesn't return a URL that has already been visited.
	 *
	 * @return
	 * @throws InterruptedException
	 */
	protected String nextUrlToDownload() throws InterruptedException {
		String nextUrl;
		do {
			nextUrl = this.urlsToDownload.take();
		} while (this.pagesVisited.contains(nextUrl) || this.forbiddenPages.contains(nextUrl));
		this.pagesVisited.add(nextUrl);
		return nextUrl;
	}

	protected boolean addUrlToDownload(String url) throws InterruptedException {

		if (!pagesVisited.contains(url)) {
			urlsToDownload.put(url);
			return true;
		}

		return false;
	}

	protected HTMLContent nextHtmlToAnalyze() throws InterruptedException {
		System.out.println("Crawler: Requested next html to analyze");
		HTMLContent result = htmlToAnalyze.take();
		//System.out.println("Crawler: next html to analyze is: " + result);
		return result;
	}

	protected void addHtmlToAnalyze(String htmlBody, String source) throws InterruptedException {
		htmlToAnalyze.put(new HTMLContent(htmlBody, source));
		System.out.println("addHtmlToAnalyzer added body at length: " + htmlBody.length());
	}

	protected void updateImages(int numberOfImages, int sizeOfImages) {

		synchronized (imageFiles) {
			imageFiles.updateCounter(numberOfImages, sizeOfImages);
		}
	}

	protected void updateVideos(int numberOfVideos, int sizeOfVideos) {

		synchronized (videoFiles) {
			videoFiles.updateCounter(numberOfVideos, sizeOfVideos);
		}
	}

	protected void updateDocuments(int numberOfDocuments, int sizeOfDocuments) {
		synchronized (documentFiles) {
			documentFiles.updateCounter(numberOfDocuments, sizeOfDocuments);
		}
	}

	protected void updatePages(int numberOfPages, int sizeOfPages) {
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

	//TODO: delete this methos as it's onlt for debbuging
	public void changeRunningStatus() {
		isCrawlerRunning = true;
	}

	public void UpdatePagesVisited(String page) {
		try {
			pagesVisited.add(page);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void addUrlToInternal(String currentLink) {
		try {
			internalLinks.add(currentLink);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void addURLToExternal(String currentLink) {
		try {
			externalLinks.add(currentLink);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void RemoveDownloaderFromWorking(int threadNumber) {
		synchronized (workingDownloaderThreadNumbers) {
			try {
				workingDownloaderThreadNumbers.remove(threadNumber);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void RemoveAnalyzerFromWorking(int threadNumber) {
		synchronized (workingAnalyzerThreadNumbers) {
			try {
				workingAnalyzerThreadNumbers.remove(threadNumber);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
}
