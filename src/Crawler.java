

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler implements Runnable {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
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
    private String targetURL;
    private boolean ignoreRobots;
    private boolean performPortScan;
    // Links from the same domain
    private int numberOfInternalLinks;
    private int numberOfExternalLinks;
    private Thread[] downloaderThreads;
    private Thread[] analyzerThreads;
    private Boolean isCrawlerRunning = false;
    private String portScanResults;
    private String allowedHost;
    private static final Pattern urlPattern = Pattern.compile(".*?(http:\\/\\/|https:\\/\\/)?(www.)?(.*?)(\\/.*)$");


    public static void main(String[] args) throws IOException {
        //Crawler aviv = new Crawler();
        //Set<String> testing = aviv.readRobotsFile("http://www.google.com/robots.txt");
        //ClientRequest testing = new ClientRequest("ynet.co.il", ClientRequest.headRequest);
        //System.out.println(testing.getResponseStatusCode());
    }

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

    public boolean ConfigureCrawler(String targetURL, boolean ignoreRobots, boolean performPortScan) {
        if (isCrawlerRunning) {
            return false;
        }

        this.targetURL = targetURL;
        this.ignoreRobots = ignoreRobots;
        this.performPortScan = performPortScan;
        return true;
    }

    public boolean isBusy() {
        return isCrawlerRunning;
    }

    public void run() {

        String result;
        if (isCrawlerRunning) {
            return;
        } else {
            synchronized (isCrawlerRunning) {
                if (isCrawlerRunning) {
                    return;
                }
                initStatistics();
                isCrawlerRunning = true;
            }
            if (performPortScan) {
                portScanResults = portScan(targetURL);
            } else {
                portScanResults = "Port scan was not selected.";
            }

            Set<String> pagesFromRobotsFile = null;
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
                forbiddenPages = new HashSet<String>();
            }

            try {
                urlsToDownload.put(targetURL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            downloaderThreads = new Thread[maxDownloaders - 1];
            analyzerThreads = new Thread[maxAnalyzers - 1];
            for (int i = 0; i < analyzerThreads.length; i++) {
                analyzerThreads[i] = new Thread(new Analyzer(this));
            }
            for (int i = 0; i < downloaderThreads.length; i++) {
                downloaderThreads[i] = new Thread(new Downloader(this));
            }

            for (int i = 0; i < analyzerThreads.length; i++) {
                analyzerThreads[i].start();
            }
            for (int i = 0; i < downloaderThreads.length; i++) {
                downloaderThreads[i].start();
            }

            while ((!urlsToDownload.isEmpty() || !htmlToAnalyze.isEmpty()) && (pagesVisited.size() < MAX_PAGES_TO_SEARCH)) {
                //Jibrish
            }


            try {
                for (int i = 0; i < analyzerThreads.length; i++) {

                    analyzerThreads[i].join();

                }
                for (int i = 0; i < downloaderThreads.length; i++) {
                    downloaderThreads[i].join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isCrawlerRunning = false;
        }

    }



    private Set<String> readRobotsFile(String targetURL) throws IOException {

        Set<String> result = new HashSet<>();
        ClientRequest connection = new ClientRequest(targetURL, ClientRequest.getRequest);
        //Connection connection = Jsoup.connect(targetURL).userAgent(USER_AGENT);
        //Document document = connection.get();
        //System.out.println("Debbug: Response code is " + connection.getResponseStatusCode());
        if (connection.getResponseStatusCode().equals("200")) {
            String[] forbiddenUrls = connection.getBody().split(ClientRequest.CRLF);
            //System.out.println(forbiddenUrls.length);
            for (String url : forbiddenUrls) {
                if (url.contains("Disallow")) {
                    int startOfSubStringIndex = url.indexOf(" ");
                    String urlToForbiddenList = url.substring(startOfSubStringIndex + 1, url.length());
                    System.out.println("Got the URL (From robots.txt): " + urlToForbiddenList);
                    result.add(urlToForbiddenList);
                }
            }
        }
        return result;
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

    protected void addUrlToDownload(String url) throws InterruptedException {

        if (!pagesVisited.contains(url)) {
            urlsToDownload.put(url);
        }
    }

    protected String nextHtmlToAnalyze() throws InterruptedException {
        return htmlToAnalyze.take();
    }

    protected void addHtmlToAnalyze(String htmlBody) throws InterruptedException {
        htmlToAnalyze.put(htmlBody);
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

    //For Debbug
    public void changeRunningStatus() {
        isCrawlerRunning = true;
    }

    public void UpdatePagesVisited(String page) {
        pagesVisited.add(page);
    }
}
