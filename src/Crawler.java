import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
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
	private AtomicInteger totalWorkLoad;
	private String startDate;
	private String startTime;
	private String rootDir;

	private int requestCount;
	private Float sumOfRTT;



	public Crawler(HashMap<String, String> crawlerConfiguration) {

		rootDir = crawlerConfiguration.get(rootKey);
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

		this.targetURL = ParseURL(targetURL);

		if (this.targetURL.charAt(this.targetURL.length() - 1) == '\\') {
			this.targetURL = this.targetURL.substring(0, this.targetURL.length() - 1);
		}
		this.ignoreRobots = ignoreRobots;
		this.performPortScan = performPortScan;
		this.externalDomains = new SynchronizedSet<>();
		return true;
	}

	public static String ParseURL(String url) {
		String parsedURL = "";
		if (!url.endsWith("/")) {
			url = url + "/";
		}

		try {
			Matcher matcher = DOMAIN_PATTERN.matcher(url);
			if (matcher.find()) {
				System.out.println();
				parsedURL = matcher.group(2);
				int indexOfFirstSlash = parsedURL.indexOf('/');
				if (indexOfFirstSlash > 0) {
					parsedURL = parsedURL.substring(0, indexOfFirstSlash);
				}

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
			pagesFromRobotsFile = readRobotsFile(targetURL +"/robots.txt");
			if (pagesFromRobotsFile != null) {
				if (ignoreRobots) {
					forbiddenPages = new SynchronizedSet<String>();
					for (String page : pagesFromRobotsFile) {
						try {
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
				addUrlToDownload(targetURL);
				addUrlToInternal(targetURL);
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
						totalWorkLoad.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}	
				}
			}

			for (Analyzer analyzer : allAnalyzers) {
				analyzer.shutdown();
			}

			for (Downloader downloader : allDownloaders) {
				downloader.shutdown();
			}
			
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
				e.printStackTrace();
			}

			createResultPage();
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
				String domain = ResultsFilenameToDomain(resultFileName);
				String fullResultWebPath = RESULTS_PATH_WEB + resultFileName;
				if (!crawledDomains.containsKey(domain)) {
					crawledDomains.put(domain, fullResultWebPath);
				} else {
					Date currentDate = ResultsFilenameToDate(resultFileName);
					Date previousDate = ResultsFilenameToDate(crawledDomains.get(domain));
					if (currentDate != null && previousDate != null) {
						if (currentDate.after(previousDate)) {
							crawledDomains.remove(domain);
							crawledDomains.put(domain, fullResultWebPath);
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
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
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
					writer.write("<li><a href=" + crawledDomains.get(externalDomain) + ">" + externalDomain + "</a></li>");
				} else {
					writer.write("<li>" + externalDomain + "</li>");
				}
			}
			writer.write("</ul></div>");

			if (performPortScan) {
				writer.write("<br/><div class=\"portScan\">");
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

		if (connection.getResponseStatusCode().equals("200")) {
			if (connection.getBody() != null) {
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
							e.printStackTrace();
						}
					}
				}

				return result;
			}
		} else if (connection.getResponseStatusCode().equals("302") || connection.getResponseStatusCode().equals("301")) {
			String newURL = connection.responseHeaderFields.get("Location");
			return readRobotsFile(newURL);
		}
		return null;
	}


	private String portScan(String targetURL) {

		StringBuilder sb = new StringBuilder();
		sb.append("Open ports: ");
		for (int port = 1; port <= MAX_PORTNUMBER_TO_SCAN; port++) {
			try {
				Socket socket = new Socket();
				socket.connect(new InetSocketAddress(targetURL, port), 100);
				sb.append(port + " ");
				socket.close();
			} catch (Exception e) {

			}

		}
		sb.deleteCharAt(sb.length() - 1);
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
		System.out.println("Downloader took next url to download. Current queue size: " + urlsToDownload.size());
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
			totalWorkLoad.incrementAndGet();
			urlsToDownload.put(url);
			return true;
		}	
		
		return false;
	}

	protected HTMLContent nextHtmlToAnalyze() throws InterruptedException {
		HTMLContent result = htmlToAnalyze.take();
		System.out.println("Analyzer took next html to analyzer. Current queue size: " + htmlToAnalyze.size());
		return result;
	}

	protected void addHtmlToAnalyze(String htmlBody, String source, String urlSource) throws InterruptedException {


		totalWorkLoad.incrementAndGet();
		htmlToAnalyze.put(new HTMLContent(htmlBody, source, urlSource));
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

	public void UpdatePagesVisited(String page) {
		try {
			pagesVisited.add(page);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void addUrlToInternal(String currentLink) {
		try {
			internalLinks.add(currentLink);
		} catch (InterruptedException e) {
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
			e.printStackTrace();
		}

	}

	public String ExtractDomain(String link) {
		Matcher domainMatcher = DOMAIN_PATTERN.matcher(link);
		if (domainMatcher.find()) {
			if (domainMatcher.group(1) == null && domainMatcher.group(2).endsWith(":")) {
				link = link + "/";
				ExtractDomain(link);
			}
			else {
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
				e.printStackTrace();
			}
		}

	}

	public void RemoveAnalyzerFromWorking(int threadNumber) {
		synchronized (workingAnalyzerThreadNumbers) {
			try {
				workingAnalyzerThreadNumbers.remove(threadNumber);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
