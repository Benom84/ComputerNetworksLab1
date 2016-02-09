

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler implements Runnable {
	public static final String RESULTS_PATH_LOCAL = "\\ScanResults\\";
	public static final String RESULTS_PATH_WEB = "/ScanResults/";
	public static final Pattern DOMAIN_PATTERN = Pattern.compile("(^[Hh][Tt][Tt][Pp].*:\\/\\/)?(.*?)(\\/.*)");
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
	protected HashMap<String, String> crawledDomains;
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
	private AtomicInteger totalWorkLoad;
	private String startDate;
	private String startTime;
	private String rootDir;

	private int requestCount;
	private Float sumOfRTT;



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
		File resultsFolderFile = new File(rootDir + RESULTS_PATH_LOCAL);
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

		this.targetURL = parseURL(targetURL);

		if (this.targetURL.charAt(this.targetURL.length() - 1) == '\\') {
			this.targetURL = this.targetURL.substring(0, this.targetURL.length() - 1);
		}
		this.ignoreRobots = ignoreRobots;
		//TODO
		System.out.println("Ignore robots is: " + this.ignoreRobots);
		this.performPortScan = performPortScan;
		this.externalDomains = new SynchronizedSet<>();
		return true;
	}

	private static String parseURL(String url) {
		String parsedURL = "";
		if (!url.endsWith("/")) {
			url = url + "/";
		}

		System.out.println("Crawler: ParseUrl: url is " + url);
		try {
			Matcher matcher = DOMAIN_PATTERN.matcher(url);
			if (matcher.find()) {
				System.out.println();
				parsedURL = matcher.group(2);
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

	public void addRTT(long currentRTT) {
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
			System.out.println("Reading pages from robots file");
			pagesFromRobotsFile = readRobotsFile(targetURL +"/robots.txt");
			System.out.println("Finished Reading pages from robots file");
			if (pagesFromRobotsFile != null) {
				System.out.println("Pages from robot file is not null");
				if (ignoreRobots) {
					forbiddenPages = new SynchronizedSet<String>();
					for (String page : pagesFromRobotsFile) {
						try {
							System.out.println("Adding forbidden pages to urls to download: " + page);
							addUrlToDownload(page);
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
				System.out.println("Target url is: " + targetURL);
				addUrlToDownload(targetURL);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			Thread[] downloaderThreads = new Thread[maxDownloaders];
			Thread[] analyzerThreads = new Thread[maxAnalyzers];
			HashSet<Downloader> allDownloaders = new HashSet<Downloader>();
			HashSet<Analyzer> allAnalyzers = new HashSet<Analyzer>();

			for (int i = 0; i < analyzerThreads.length; i++) {
				Analyzer analyzer = new Analyzer(this);
				allAnalyzers.add(analyzer);
				analyzerThreads[i] = new Thread(analyzer);
				analyzerThreads[i].start();
			}

			for (int i = 0; i < downloaderThreads.length; i++) {
				Downloader downloader = new Downloader(this);
				allDownloaders.add(downloader);
				downloaderThreads[i] = new Thread(downloader);
				downloaderThreads[i].start();
			}


			while (totalWorkLoad.intValue() != 0) {

				synchronized (totalWorkLoad) {
					try {
						System.out.println("###########################################Workload is not 0");
						totalWorkLoad.wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
				}
			}
			System.out.println("###########################################Workload is 0! Actual size: " + totalWorkLoad.intValue());
			System.out.println("###########################################Signaling analyzers to shutdown.");
			for (Analyzer analyzer : allAnalyzers) {
				analyzer.shutdown();
			}
			System.out.println("###########################################Signaling downloaders to shutdown.");
			for (Downloader downloader : allDownloaders) {
				downloader.shutdown();
			}
			/*
			System.out.println("###########################################Signaling downloaders to wakeup.");
			synchronized (urlsToDownload) {
				urlsToDownload.notifyAll();
			}

			System.out.println("###########################################Signaling analyzers to wakeup.");
			synchronized (htmlToAnalyze) {
				htmlToAnalyze.notifyAll();
			}
			 */
			try {
				for (Thread thread : analyzerThreads) {
					thread.interrupt();
					thread.join();
				}
				for (Thread thread : downloaderThreads) {
					thread.interrupt();
					thread.join();
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			System.out.println("All threads have joined");
			System.out.println("Creating results page");
			createResultPage();

			System.out.println("*************************************************************************************************");
			System.out.println("Debug results");
			System.out.println("*************************************************************************************************");
			System.out.println("Internal Links");
			for (String link : internalLinks) {
				System.out.println(link);
			}
			System.out.println("*************************************************************************************************");
			System.out.println("External Links");
			for (String link : externalLinks) {
				System.out.println(link);
			}
			System.out.println("*************************************************************************************************");
			System.out.println("External Domains");
			for (String link : externalDomains) {
				System.out.println(link);
			}
			System.out.println("*************************************************************************************************");
			isCrawlerRunning = false;
		}
	}


	private void readCrawledDomains() {
		crawledDomains = new HashMap<String, String>();
		File resultsPath = new File(rootDir + RESULTS_PATH_LOCAL);
		if (resultsPath.exists() && resultsPath.isDirectory()) {
			File[] resultsFiles = resultsPath.listFiles();
			for (File resultFile : resultsFiles) {
				String resultFileName = resultFile.getName();
				System.out.println("Result File found for domain: " + resultFileName);
				String domain = ResultsFilenameToDomain(resultFileName);
				System.out.println("Domain: " + resultFileName);
				if (!crawledDomains.containsKey(domain)) {
					crawledDomains.put(domain, resultFileName);
				} else {
					Date currentDate = ResultsFilenameToDate(resultFileName);
					Date previousDate = ResultsFilenameToDate(crawledDomains.get(domain));
					if (currentDate != null && previousDate != null) {
						if (currentDate.after(previousDate)) {
							crawledDomains.replace(domain, resultFileName);
						}
					}
				}
				
			}
		}

	}
	
	public static String ResultsFilenameToDomain(String resultFileName) {
		int length = resultFileName.length();
		String domain = resultFileName.substring(0, length - 25);
		return domain;
	}

	public static Date ResultsFilenameToDate(String resultsFileName) {
		
		// www.morfix.co.il_2016_02_07_17_41_55.html
		int length = resultsFileName.length();
		String dateFromFile = resultsFileName.substring(length - 24, length - 5);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss");
		try {
			Date parsedDate = formatter.parse(dateFromFile);
			return parsedDate;
		} catch (ParseException e) {
			e.printStackTrace();
			System.out.println("Error parsing the date: " + dateFromFile);
			return null;
		}
	}

	private void createResultPage() {
		String resultPageName = targetURL + "_" + startDate + "_" + startTime + ".html";
		String resultPath = rootDir + RESULTS_PATH_LOCAL + resultPageName;
		float averageRTT = sumOfRTT / requestCount;
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(resultPath), "utf-8"))) {
			writer.write("<!DOCTYPE html><html><head lang=\"en\"><meta charset=\"UTF-8\"><title>");
			writer.write(targetURL + " " + startDate + " " + startTime);
			writer.write("</title></head><link href=\"../css/style.css\" rel=\"stylesheet\" /><body><div class=\"header\">");
			writer.write("<h1>" + targetURL + " " + startDate + " " + startTime + "</h1></div>");
			writer.write("<div class=\"resultsTable\"><table><tr><td>Was Robots file respected?</td><td>" + !ignoreRobots + "</td></tr></table>");
			writer.write("<table><tr><td>Category</td><td>Number</td><td>Size</td></tr>");
			writer.write("<tr><td>Images</td><td>" + imageFiles.getNumberOfFiles() + "</td><td>" + imageFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Videos</td><td>" + videoFiles.getNumberOfFiles() + "</td><td>" + videoFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Documents</td><td>" + documentFiles.getNumberOfFiles() + "</td><td>" + documentFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("<tr><td>Pages</td><td>" + pagesFiles.getNumberOfFiles() + "</td><td>" + pagesFiles.getSizeOfFiles() + "</td></tr>");
			writer.write("</table></div><br/>");
			writer.write("<div class=\"domainStatistics\">");
			writer.write("<h2>Number of Internal Links:\t\t\t\t" + internalLinks.size() + "</h2>");
			writer.write("<h2>Number of External Links:\t\t\t\t" + externalLinks.size() + "</h2>");
			writer.write("<h2>Number of External Domains:\t\t\t\t" + externalDomains.size() + "</h2>");
			writer.write("<h2>Average RTT:\t\t\t\t" + averageRTT + "ms</h2></div>");
			writer.write("<div class=\"connectedDomains\">");
			writer.write("<h2>The Domains connected:</h2>");
			writer.write("<ul>");
			for (String externalDomain : externalDomains) {
				if (crawledDomains.containsKey(externalDomain)) {
					writer.write("<li><a href=/" + crawledDomains.get(externalDomain) + "\">" + externalDomain + "</a>" + externalDomain + "</li>");
				} else {
					writer.write("<li>" + externalDomain + "</li>");
				}

			}
			writer.write("</ul>");
			
			if (performPortScan) {
				writer.write("</div><br/><div class=\"portScan\">");
				writer.write("<h1> Port Scan Results</h1>");
				writer.write("<h2>" + portScanResults + "</h2>");
				writer.write("</div>");
			}
			
			writer.write("<br/><div class=\"goBack\">");
			writer.write("<h3><a href = \"/\">Back To Main Page</a></h3></div>");
			writer.write("</body></html>");


		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	private SynchronizedSet<String> readRobotsFile(String targetURL) {

		SynchronizedSet<String> result = new SynchronizedSet<String>();
		ClientRequest connection;
		try {
			connection = new ClientRequest(targetURL, ClientRequest.getRequest);
		} catch (IOException e1) {
			System.out.println("Error connecting to: " + targetURL + " to get robots file.");
			e1.printStackTrace();
			return null;
		}
		//Connection connection = Jsoup.connect(targetURL).userAgent(USER_AGENT);
		//Document document = connection.get();
		//System.out.println("Debbug: Response code is " + connection.getResponseStatusCode());
		System.out.println("Robots: " + connection.getResponseStatusCode());
		if (connection.getResponseStatusCode().equals("200")) {
			if (connection.getBody() != null) {
				System.out.println("**********************************************************************");
				System.out.println(connection.getBody());
				System.out.println("**********************************************************************");
				String[] forbiddenUrls = connection.getBody().split(ClientRequest.CRLF);
				for (String url : forbiddenUrls) {
					if (url.contains("Disallow")) {
						int startOfSubStringIndex = url.indexOf(" ");
						String urlToForbiddenList = url.substring(startOfSubStringIndex + 1, url.length());
						if (!urlToForbiddenList.startsWith("/")) {
							urlToForbiddenList = "/" + urlToForbiddenList;
						}
						System.out.println("Got the URL (From robots.txt): " + connection.host + urlToForbiddenList);
						try {
							result.add(connection.host + urlToForbiddenList);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}

				return result;
			}

			//System.out.println(forbiddenUrls.length);

		} else if (connection.getResponseStatusCode().equals("302") || connection.getResponseStatusCode().equals("301")) {
			String newURL = connection.responseHeaderFields.get("Location");
			System.out.println("Robots: response was 301/302 so new url is: " + newURL);
			return readRobotsFile(newURL);
		}
		return null;
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
				System.out.print(port + ", ");
				socket.close();
			} catch (Exception e) {

			}

		}
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
		totalWorkLoad = new AtomicInteger();
		imageFiles.init();
		videoFiles.init();
		documentFiles.init();
		pagesFiles.init();

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
		nextUrl = this.urlsToDownload.take();
		this.pagesVisited.add(nextUrl);
		return nextUrl;
	}

	protected boolean addUrlToDownload(String url) throws InterruptedException {

		boolean allowedUrl = true;
		allowedUrl = !pagesVisited.contains(url);

		if (allowedUrl) {
			for (String forbiddenPage : forbiddenPages) {
				if (url.startsWith(forbiddenPage)) {
					allowedUrl = false;
					break;
				}
			}
		}
		if (allowedUrl)		{
			//TODO delete temp
			int temp = totalWorkLoad.incrementAndGet();
			System.out.println("#################Workload updated and is now: " + temp);
			urlsToDownload.put(url);
			System.out.println("#################urlsToDownload updated and is now: " + urlsToDownload.size());
			return true;
		}	else {
			System.out.println("Crawler: addUrlToDownload: did not add the next url: " + url);
		}


		return false;
	}

	protected HTMLContent nextHtmlToAnalyze() throws InterruptedException {
		System.out.println("Crawler: Requested next html to analyze.");
		HTMLContent result = htmlToAnalyze.take();
		System.out.println("Crawler: next html to analyze is sized: " + result.GetHTML().length());
		return result;
	}

	protected void addHtmlToAnalyze(String htmlBody, String source) throws InterruptedException {

		//TODO delete temp
		int temp = totalWorkLoad.incrementAndGet();
		System.out.println("#################Workload updated and is now: " + temp);
		htmlToAnalyze.put(new HTMLContent(htmlBody, source));
		System.out.println("addHtmlToAnalyzer added body at length: " + htmlBody.length());


	}

	protected int lowerWorkload() {
		int result;
		synchronized (totalWorkLoad) {
			result = totalWorkLoad.decrementAndGet();
			if (totalWorkLoad.intValue() == 0) {
				totalWorkLoad.notifyAll();
			}
		}

		return result;
	}

	protected void updateImages(int numberOfImages, int sizeOfImages) {

		synchronized (imageFiles) {
			imageFiles.updateCounter(numberOfImages, sizeOfImages);
			System.out.println("Image statistics in updated: " + imageFiles.getSizeOfFiles());
		}
	}

	protected void updateVideos(int numberOfVideos, int sizeOfVideos) {

		synchronized (videoFiles) {
			videoFiles.updateCounter(numberOfVideos, sizeOfVideos);
			System.out.println("Video statistics in updated: " + videoFiles.getSizeOfFiles());
		}
	}

	protected void updateDocuments(int numberOfDocuments, int sizeOfDocuments) {
		synchronized (documentFiles) {
			documentFiles.updateCounter(numberOfDocuments, sizeOfDocuments);
			System.out.println("Documents statistics in updated: " + documentFiles.getSizeOfFiles());
		}
	}

	protected void updatePages(int numberOfPages, int sizeOfPages) {
		synchronized (pagesFiles) {
			pagesFiles.updateCounter(numberOfPages, sizeOfPages);
			System.out.println("Pages statistics in updated: " + pagesFiles.getSizeOfFiles());
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
			if (!currentLink.endsWith("/")) {
				currentLink = currentLink + "/";
			}
			String domain = ExtractDomain(currentLink);
			if (domain != null) {
				externalDomains.add(domain);
			}




		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public String ExtractDomain(String link) {
		System.out.println("Crawler: ExtractDomain: got link: " + link);
		Matcher domainMatcher = DOMAIN_PATTERN.matcher(link);
		if (domainMatcher.find()) {
			System.out.println("Crawler: ExtractDomain: found match.");
			if (domainMatcher.group(1) == null && domainMatcher.group(2).endsWith(":")) {
				link = link + "/";
				ExtractDomain(link);
			}
			else {
				System.out.println("Crawler: ExtractDomain: returning: " + domainMatcher.group(2));
				return domainMatcher.group(2);
			}
		}

		return null;
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
